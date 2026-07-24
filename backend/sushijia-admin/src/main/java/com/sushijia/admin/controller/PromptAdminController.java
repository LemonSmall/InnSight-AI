package com.sushijia.admin.controller;

import com.sushijia.common.response.R;
import com.sushijia.repository.entity.PromptTemplate;
import com.sushijia.repository.entity.StyleLibrary;
import com.sushijia.repository.mapper.PromptTemplateMapper;
import com.sushijia.repository.mapper.StyleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 后台 - 提示词模板 + 风格库管理
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class PromptAdminController {

    private final PromptTemplateMapper promptMapper;
    private final StyleMapper styleMapper;

    // ====== 模板管理 ======
    @GetMapping("/prompts")
    public R<List<PromptTemplate>> listPrompts() {
        return R.ok(promptMapper.selectList(null));
    }

    @GetMapping("/prompts/{moduleKey}")
    public R<List<PromptTemplate>> getVersions(@PathVariable("moduleKey") String moduleKey) {
        return R.ok(promptMapper.findVersionsByModule(moduleKey));
    }

    @PostMapping("/prompts")
    public R<PromptTemplate> createPrompt(@RequestBody PromptTemplate template) {
        template.setVersion("v" + (promptMapper.findVersionsByModule(template.getModuleKey()).size() + 1));
        promptMapper.insert(template);
        return R.ok(template);
    }

    @PutMapping("/prompts/{id}")
    public R<String> updatePrompt(@PathVariable("id") Long id, @RequestBody PromptTemplate template) {
        template.setId(id);
        promptMapper.updateById(template);
        return R.ok("ok");
    }

    @PutMapping("/prompts/{id}/rollback")
    public R<String> rollback(@PathVariable("id") Long id) {
        PromptTemplate toRollback = promptMapper.selectById(id);
        if (toRollback == null) return R.fail(404, "版本不存在");

        // 将当前 production 版本标记为 rolled_back
        List<PromptTemplate> versions = promptMapper.findVersionsByModule(toRollback.getModuleKey());
        for (PromptTemplate v : versions) {
            if ("production".equals(v.getStatus())) {
                v.setStatus("rolled_back");
                promptMapper.updateById(v);
            }
        }

        // 将目标版本设为 production
        toRollback.setStatus("production");
        toRollback.setGrayPercent(100);
        promptMapper.updateById(toRollback);
        return R.ok("回滚成功");
    }

    // ====== 风格库管理 ======
    @GetMapping("/styles")
    public R<List<StyleLibrary>> listStyles() {
        return R.ok(styleMapper.selectList(null));
    }

    @PostMapping("/styles")
    public R<StyleLibrary> createStyle(@RequestBody StyleLibrary style) {
        styleMapper.insert(style);
        return R.ok(style);
    }

    @PutMapping("/styles/{id}")
    public R<String> updateStyle(@PathVariable("id") Long id, @RequestBody StyleLibrary style) {
        style.setId(id);
        styleMapper.updateById(style);
        return R.ok("ok");
    }
}
