package com.example.docmerge.service;

import com.example.docmerge.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 临时文件仓库。
 *
 * <p>文件保存在 java.io.tmpdir/docx-merge（可通过 app.storage.dir 覆盖），
 * 使用 UUID 作为 fileId，并写同名 .json sidecar，便于进程重启后继续下载。</p>
 */
@Component
public class TempFileStore {

    private static final Logger log = LoggerFactory.getLogger(TempFileStore.class);
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private final Path root;
    private final Duration retention;
    private final ObjectMapper objectMapper;
    private final Map<String, StoredFile> index = new ConcurrentHashMap<>();

    public TempFileStore(AppProperties properties, ObjectMapper objectMapper) {
        this.root = Path.of(properties.getStorage().getDir()).toAbsolutePath().normalize();
        this.retention = Duration.ofHours(Math.max(1, properties.getStorage().getRetentionHours()));
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(root);
            log.info("临时文件目录: {}", root);
            cleanupExpired();
        } catch (IOException e) {
            log.error("初始化临时文件目录失败: {}", root, e);
            throw new IllegalStateException("无法创建临时文件目录", e);
        }
    }

    /**
     * 为指定 fileId 分配输出路径，调用方负责写入。
     */
    public Path allocate(String fileId) {
        if (!isValidFileId(fileId)) {
            throw new IllegalArgumentException("非法 fileId");
        }
        return resolve(fileId, ".docx");
    }

    /**
     * 登记已生成的文件，并写 sidecar 元数据。
     */
    public StoredFile register(String fileId, String fileName, Path path) throws IOException {
        long size = Files.size(path);
        Instant now = Instant.now();
        StoredFile stored = new StoredFile(fileId, fileName, path.normalize(), size, now);
        index.put(fileId, stored);
        Sidecar sidecar = new Sidecar(fileId, fileName, size, now);
        objectMapper.writeValue(resolve(fileId, ".json").toFile(), sidecar);
        return stored;
    }

    public Optional<StoredFile> find(String fileId) {
        if (!isValidFileId(fileId)) {
            return Optional.empty();
        }
        StoredFile cached = index.get(fileId);
        if (cached != null && Files.isRegularFile(cached.path())) {
            return Optional.of(cached);
        }
        Path docx = resolve(fileId, ".docx");
        Path meta = resolve(fileId, ".json");
        if (!Files.isRegularFile(docx) || !Files.isRegularFile(meta)) {
            return Optional.empty();
        }
        try {
            Sidecar sidecar = objectMapper.readValue(meta.toFile(), Sidecar.class);
            StoredFile stored = new StoredFile(fileId, sidecar.fileName(), docx,
                    Files.size(docx), sidecar.createdAt());
            index.put(fileId, stored);
            return Optional.of(stored);
        } catch (IOException e) {
            log.warn("读取 sidecar 失败: fileId={}", fileId, e);
            return Optional.empty();
        }
    }

    public boolean delete(String fileId) {
        if (!isValidFileId(fileId)) {
            return false;
        }
        StoredFile removed = index.remove(fileId);
        boolean deleted = deleteQuietly(resolve(fileId, ".docx"));
        deleted |= deleteQuietly(resolve(fileId, ".json"));
        return deleted || removed != null;
    }

    /**
     * 每小时清理超过保留时间的临时文件；启动时也执行一次。
     */
    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 3_600_000L)
    public void cleanupExpired() {
        Instant cutoff = Instant.now().minus(retention);
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isRegularFile).forEach(files::add);
        } catch (IOException e) {
            log.warn("扫描临时文件目录失败: {}", root, e);
            return;
        }
        files.sort(Comparator.comparing(Path::getFileName));
        for (Path file : files) {
            try {
                FileTime lastModified = Files.getLastModifiedTime(file);
                if (lastModified.toInstant().isAfter(cutoff)) {
                    continue;
                }
                String name = file.getFileName().toString();
                if (name.endsWith(".docx")) {
                    String id = name.substring(0, name.length() - ".docx".length());
                    index.remove(id);
                    deleteQuietly(resolve(id, ".json"));
                }
                if (Files.deleteIfExists(file)) {
                    log.info("清理过期临时文件: {}", file.getFileName());
                }
            } catch (IOException e) {
                log.warn("清理临时文件失败: {}", file.getFileName(), e);
            }
        }
    }

    private Path resolve(String fileId, String suffix) {
        Path path = root.resolve(fileId + suffix).normalize();
        if (!path.startsWith(root)) {
            throw new SecurityException("检测到路径穿越");
        }
        return path;
    }

    private boolean deleteQuietly(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("删除临时文件失败: {}", path.getFileName(), e);
            return false;
        }
    }

    public static boolean isValidFileId(String fileId) {
        return fileId != null && UUID_PATTERN.matcher(fileId).matches()
                && UUID.fromString(fileId).toString().equalsIgnoreCase(fileId);
    }

    public Path root() {
        return root;
    }

    public record StoredFile(String fileId, String fileName, Path path, long size, Instant createdAt) {
    }

    private record Sidecar(String fileId, String fileName, long size, Instant createdAt) {
    }
}
