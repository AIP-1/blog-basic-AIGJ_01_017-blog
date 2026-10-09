package com.nhnacademy.blog.image.application;

import com.nhnacademy.blog.global.config.UploadProperties;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.image.domain.Image;
import com.nhnacademy.blog.image.domain.ImageRepository;
import com.nhnacademy.blog.image.domain.ImageType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 올리기 (T036, POST-05, research R-15). 본문·프로필·블로그 이미지가 같이 쓴다.
 * <ol>
 *   <li>10MB 이하인지, 파일 앞부분(매직 넘버)으로 jpg/png/gif/webp인지, 실제로 이미지로 읽히는지 본다.</li>
 *   <li>원본: jpg·png는 긴 변 1920px로 줄이고 EXIF 방향을 픽셀에 반영해 저장한다. gif(애니메이션)·webp는 그대로.</li>
 *   <li>썸네일: 긴 변 400px. jpg는 jpg, 나머지는 png.</li>
 *   <li>파일 이름은 UUID라 원래 이름이 겹치거나 경로 문자를 넣어도 상관없다. 원래 이름은 DB에만 둔다.</li>
 * </ol>
 */
@Service
public class ImageService {

    public static final long MAX_SIZE = 10L * 1024 * 1024;
    static final int MAX_SIDE = 1920;
    static final int THUMBNAIL_SIDE = 400;
    private static final String URL_PREFIX = "/uploads/";

    private final ImageRepository imageRepository;
    private final Path uploadDir;

    public ImageService(ImageRepository imageRepository, UploadProperties uploadProperties) {
        this.imageRepository = imageRepository;
        this.uploadDir = uploadProperties.dir().toAbsolutePath().normalize();
    }

    @Transactional
    public Image upload(Long uploaderId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.invalidField("file", "이미지 파일을 골라 주세요.");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE);
        }
        byte[] bytes = readAll(file);
        ImageType type = ImageType.detect(Arrays.copyOf(bytes, Math.min(bytes.length, 12)))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_IMAGE));
        // 이름의 확장자도 실제 형식과 같아야 한다(a.html, 확장자 없음, PNG 내용인 a.jpg 모두 거절). 저장 이름은 여전히 서버가 정한다
        if (!type.matchesFileName(file.getOriginalFilename())) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE);
        }
        int longestSide = longestSide(bytes);

        String name = UUID.randomUUID().toString();
        String originalFile = name + "." + type.getExtension();
        String thumbnailFile = "t_" + name + "." + type.getThumbnailFormat();
        try {
            Files.createDirectories(uploadDir);
            writeOriginal(bytes, type, longestSide, uploadDir.resolve(originalFile));
            writeThumbnail(bytes, type, longestSide, uploadDir.resolve(thumbnailFile));
            return imageRepository.save(Image.uploaded(uploaderId, URL_PREFIX + originalFile,
                    URL_PREFIX + thumbnailFile, originalName(file), type.getContentType(), bytes.length));
        } catch (IOException e) {
            deleteFiles(originalFile, thumbnailFile);
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            deleteFiles(originalFile, thumbnailFile);
            throw e;
        }
    }

    /** 이미지로 읽히는지 보고 긴 변 길이를 돌려준다. 전체를 풀지 않고 머리 정보만 읽는다. 읽을 수 없으면 400. */
    private int longestSide(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                return Math.max(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            // 앞부분만 이미지처럼 꾸민 파일 등
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE);
        }
    }

    private void writeOriginal(byte[] bytes, ImageType type, int longestSide, Path target) throws IOException {
        if (!type.resizable()) {
            Files.write(target, bytes);
            return;
        }
        // Thumbnailator는 JPEG의 EXIF 방향을 읽어 픽셀을 돌린다(휴대폰 사진이 눕지 않게)
        Thumbnails.Builder<?> builder = Thumbnails.of(new ByteArrayInputStream(bytes));
        fit(builder, longestSide, MAX_SIDE).outputFormat(type.getExtension()).outputQuality(0.9).toFile(target.toFile());
    }

    private void writeThumbnail(byte[] bytes, ImageType type, int longestSide, Path target) throws IOException {
        Thumbnails.Builder<?> builder;
        if (type.resizable()) {
            builder = Thumbnails.of(new ByteArrayInputStream(bytes));
        } else {
            // GIF는 첫 장면, WebP는 TwelveMonkeys 플러그인으로 읽는다
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE);
            }
            builder = Thumbnails.of(image);
        }
        fit(builder, longestSide, THUMBNAIL_SIDE).outputFormat(type.getThumbnailFormat()).toFile(target.toFile());
    }

    /** 긴 변이 limit보다 크면 limit 안으로 줄이고, 작으면 그대로(키우지 않는다). */
    private static Thumbnails.Builder<?> fit(Thumbnails.Builder<?> builder, int longestSide, int limit) {
        return longestSide > limit ? builder.size(limit, limit) : builder.scale(1.0);
    }

    private static byte[] readAll(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String originalName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "image";
        }
        // 경로가 붙어 오는 브라우저도 있어 마지막 이름만 남기고, 컬럼 길이(255)에 맞춘다
        String last = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        return last.length() > 255 ? last.substring(last.length() - 255) : last;
    }

    /** 파일만 남고 DB 행이 없는 고아 파일이 생기지 않게 지운다. */
    private void deleteFiles(String... names) {
        for (String name : names) {
            deleteQuietly(uploadDir.resolve(name));
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 지우지 못한 파일은 남아도 서비스에는 영향이 없다
        }
    }

}
