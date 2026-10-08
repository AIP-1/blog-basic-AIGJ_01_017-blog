package com.nhnacademy.blog.image.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<Image, Long> {

    /** 본문 img src(= path)들로 이미지를 찾는다. 목록의 썸네일을 한 번에 구할 때 쓴다. */
    List<Image> findByPathIn(Collection<String> paths);

}
