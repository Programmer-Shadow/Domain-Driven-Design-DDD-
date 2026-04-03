package com.example.orderservice.interfaces.rest;

import com.example.orderservice.domain.model.CustomerInfo;
import com.example.orderservice.infrastructure.acl.userservice.client.UserServiceFeignClient;
import com.example.orderservice.infrastructure.acl.userservice.client.dto.ExternalUserDTO;
import com.example.orderservice.infrastructure.acl.userservice.translator.UserTranslator;
import feign.FeignException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * ACL 调试接口：展示防腐层的翻译过程
 * 仅用于学习演示，生产环境应移除
 */
@RestController
@RequestMapping("/api/debug/acl")
public class AclDebugController {

    private final UserServiceFeignClient feignClient;
    private final UserTranslator translator;

    public AclDebugController(UserServiceFeignClient feignClient, UserTranslator translator) {
        this.feignClient = feignClient;
        this.translator = translator;
    }

    /**
     * 展示 ACL 翻译的完整过程
     * 返回：外部原始数据 → 字段映射规则 → 内部领域模型
     */
    @GetMapping("/translate/{userId}")
    public ResponseEntity<Map<String, Object>> showTranslation(@PathVariable("userId") String userId) {
        try {
            // Step 1: 调用外部服务，获取原始响应
            ExternalUserDTO raw = feignClient.getUserById(userId);

            // Step 2: 翻译为内部模型
            CustomerInfo translated = translator.toCustomerInfo(raw);

            Map<String, Object> result = new LinkedHashMap<>();

            // 阶段1：User Service 原始 API 响应
            Map<String, Object> externalData = new LinkedHashMap<>();
            externalData.put("userId", raw.userId());
            externalData.put("username", raw.username());
            externalData.put("email", raw.email());
            externalData.put("street", raw.street());
            externalData.put("city", raw.city());
            externalData.put("province", raw.province());
            externalData.put("zipCode", raw.zipCode());
            externalData.put("country", raw.country());
            externalData.put("registeredAt", raw.registeredAt());
            result.put("step1_externalApiResponse", externalData);

            // 阶段2：字段映射规则
            List<Map<String, String>> mappings = new ArrayList<>();
            mappings.add(Map.of(
                "from", "userId", "fromValue", str(raw.userId()),
                "to", "customerId", "toValue", str(translated.customerId()),
                "type", "KEEP", "description", "字段保持，ID 直传"
            ));
            mappings.add(Map.of(
                "from", "username", "fromValue", str(raw.username()),
                "to", "customerName", "toValue", str(translated.customerName()),
                "type", "RENAME", "description", "语义对齐：用户域的 username → 订单域的 customerName"
            ));
            mappings.add(Map.of(
                "from", "email", "fromValue", str(raw.email()),
                "to", "email", "toValue", str(translated.email()),
                "type", "KEEP", "description", "字段保持，email 直传"
            ));
            mappings.add(Map.of(
                "from", "street, city, province, zipCode, country",
                "fromValue", String.format("%s, %s, %s, %s, %s",
                    str(raw.street()), str(raw.city()), str(raw.province()),
                    str(raw.zipCode()), str(raw.country())),
                "to", "defaultAddress (CustomerAddress)",
                "toValue", String.format("{street:%s, city:%s, province:%s, zipCode:%s, country:%s}",
                    translated.defaultAddress().street(), translated.defaultAddress().city(),
                    translated.defaultAddress().province(), translated.defaultAddress().zipCode(),
                    translated.defaultAddress().country()),
                "type", "RESTRUCTURE", "description", "结构重组：5个扁平字段 → CustomerAddress 值对象"
            ));
            mappings.add(Map.of(
                "from", "registeredAt", "fromValue", str(raw.registeredAt()),
                "to", "(dropped)", "toValue", "N/A",
                "type", "DROPPED", "description", "选择性投影：订单域不需要用户注册时间"
            ));
            result.put("step2_fieldMappings", mappings);

            // 阶段3：内部领域模型
            Map<String, Object> internalData = new LinkedHashMap<>();
            internalData.put("customerId", translated.customerId());
            internalData.put("customerName", translated.customerName());
            internalData.put("email", translated.email());
            Map<String, String> addressMap = new LinkedHashMap<>();
            addressMap.put("street", translated.defaultAddress().street());
            addressMap.put("city", translated.defaultAddress().city());
            addressMap.put("province", translated.defaultAddress().province());
            addressMap.put("zipCode", translated.defaultAddress().zipCode());
            addressMap.put("country", translated.defaultAddress().country());
            internalData.put("defaultAddress", addressMap);
            result.put("step3_domainModel", internalData);

            return ResponseEntity.ok(result);

        } catch (FeignException.NotFound e) {
            return ResponseEntity.notFound().build();
        } catch (FeignException e) {
            return ResponseEntity.internalServerError().body(
                Map.of("error", "User Service 不可用: " + e.getMessage())
            );
        }
    }

    private String str(Object value) {
        return value != null ? value.toString() : "null";
    }
}
