package com.nhnacademy.blog.blog.application;

import com.nhnacademy.blog.blog.domain.BlogSidebarModule;
import com.nhnacademy.blog.blog.domain.BlogSidebarModuleRepository;
import com.nhnacademy.blog.blog.domain.SidebarModuleType;
import com.nhnacademy.blog.global.error.BusinessException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이드바 모듈 순서와 표시 여부 (T086, BLOG-05). 블로그를 만들 때 8개 행을 함께 만들고(새 3종은 숨김),
 * 그 전에 만든 블로그는 V4 마이그레이션이 채웠다. 행이 없는 블로그(테스트가 직접 만든 블로그 등)는 기본 순서로 본다.
 */
@Service
public class SidebarModules {

    private final BlogSidebarModuleRepository repository;

    public SidebarModules(BlogSidebarModuleRepository repository) {
        this.repository = repository;
    }

    /** 새 블로그의 8개 행. 블로그 개설 트랜잭션 안에서 부른다. */
    @Transactional
    public void createDefaults(Long blogId) {
        repository.saveAll(defaults().stream()
                .map(slot -> BlogSidebarModule.of(blogId, slot.type(), slot.type().ordinal(), slot.visible()))
                .toList());
    }

    /** 8개 전부, 주인이 정한 순서로(숨긴 것 포함). 행이 빠진 종류는 기본 값으로 끝에 붙인다. */
    @Transactional(readOnly = true)
    public List<Slot> of(Long blogId) {
        List<Slot> slots = new ArrayList<>(repository.findByBlogIdOrderBySortOrderAscIdAsc(blogId).stream()
                .map(module -> new Slot(module.getModuleType(), module.isVisible()))
                .toList());
        Set<SidebarModuleType> present = slots.stream().map(Slot::type)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(SidebarModuleType.class)));
        defaults().stream().filter(slot -> !present.contains(slot.type())).forEach(slots::add);
        return slots;
    }

    /**
     * 전체 교체 (PUT /api/blog/sidebar/modules). 8종이 정확히 한 번씩이 아니거나 PROFILE을 숨기면 400.
     * 있는 행은 순서·표시만 바꾸고, 없는 행은 만든다(UNIQUE blog_id, module_type라 지우고 다시 넣지 않는다).
     */
    @Transactional
    public void replace(Long blogId, List<Slot> requested) {
        validate(requested);
        Map<SidebarModuleType, BlogSidebarModule> existing = repository.findByBlogIdOrderBySortOrderAscIdAsc(blogId)
                .stream().collect(Collectors.toMap(BlogSidebarModule::getModuleType, Function.identity()));
        for (int i = 0; i < requested.size(); i++) {
            Slot slot = requested.get(i);
            BlogSidebarModule module = existing.get(slot.type());
            if (module == null) {
                repository.save(BlogSidebarModule.of(blogId, slot.type(), i, slot.visible()));
            } else {
                module.place(i, slot.visible());
            }
        }
    }

    /** 요청의 moduleType 문자열을 읽는다. 모르는 값이면 400(modules). */
    public static SidebarModuleType parse(String value) {
        return Arrays.stream(SidebarModuleType.values())
                .filter(type -> type.name().equals(value))
                .findFirst()
                .orElseThrow(() -> BusinessException.invalidField("modules", "알 수 없는 모듈입니다: " + value));
    }

    private static void validate(List<Slot> requested) {
        Set<SidebarModuleType> types = requested.stream().map(Slot::type)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(SidebarModuleType.class)));
        if (requested.size() != SidebarModuleType.values().length || types.size() != requested.size()) {
            throw BusinessException.invalidField("modules", "모듈 8종을 한 번씩 보내 주세요.");
        }
        boolean profileHidden = requested.stream()
                .anyMatch(slot -> slot.type() == SidebarModuleType.PROFILE && !slot.visible());
        if (profileHidden) {
            throw BusinessException.invalidField("modules", "블로그 홈 바로가기는 숨길 수 없습니다.");
        }
    }

    private static List<Slot> defaults() {
        return Arrays.stream(SidebarModuleType.values())
                .map(type -> new Slot(type, type.isVisibleByDefault()))
                .toList();
    }

    public record Slot(SidebarModuleType type, boolean visible) {
    }

}
