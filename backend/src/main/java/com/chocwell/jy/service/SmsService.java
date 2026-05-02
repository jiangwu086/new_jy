package com.chocwell.jy.service;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 短信验证码服务（阿里云短信 SMS）
 *
 * Maven 依赖已在 pom.xml 中添加：
 *   com.aliyun:dysmsapi20170525
 *
 * 配置项（application.yml）：
 *   app.sms.access-key-id
 *   app.sms.access-key-secret
 *   app.sms.sign-name              短信签名（需在阿里云控制台审核）
 *   app.sms.template-code-register 注册/登录场景模板
 *   app.sms.template-code-reset    重置/换绑场景模板
 *
 * 调用方式：sendCode(phone) 默认 register；sendCode(phone, "reset") 走重置模板。
 */
@Slf4j
@Service
public class SmsService {

    /** 短信场景常量 */
    public static final String TYPE_REGISTER = "register";
    public static final String TYPE_RESET    = "reset";

    private static final String REDIS_KEY_PREFIX  = "sms:code:";
    private static final long   CODE_EXPIRE_MINUTES = 5;
    private static final int    CODE_LENGTH         = 6;

    @Value("${app.sms.access-key-id}")
    private String accessKeyId;

    @Value("${app.sms.access-key-secret}")
    private String accessKeySecret;

    @Value("${app.sms.sign-name}")
    private String signName;

    @Value("${app.sms.template-code-register:}")
    private String templateCodeRegister;

    @Value("${app.sms.template-code-reset:}")
    private String templateCodeReset;

    private final StringRedisTemplate redisTemplate;

    public SmsService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 默认调用：注册/登录场景
     */
    public void sendCode(String phone) {
        sendCode(phone, TYPE_REGISTER);
    }

    /**
     * 发送验证码（按场景使用对应模板）
     * @param phone 手机号
     * @param type  register 注册/登录 | reset 重置/换绑;为空或未识别值默认走 register
     */
    public void sendCode(String phone, String type) {
        String code = generateCode();

        // 存入 Redis（覆盖旧 code，重置 TTL）
        String redisKey = REDIS_KEY_PREFIX + phone;
        redisTemplate.opsForValue().set(redisKey, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        // ── 开发模式：凭证未配置时跳过真实发送 ──────────────────────
        if (isDevMode()) {
            log.warn("【开发模式】短信未发送，验证码: phone={}, type={}, code={}", phone, type, code);
            // 开发模式下同时接受固定码 000000，方便不看日志时使用
            redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + phone + ":dev0", "000000",
                                            CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            return;
        }

        // 选模板：reset 走重置模板，其余一律 register
        String templateCode = TYPE_RESET.equalsIgnoreCase(type)
                ? (templateCodeReset != null && !templateCodeReset.isBlank() ? templateCodeReset : templateCodeRegister)
                : templateCodeRegister;
        if (templateCode == null || templateCode.isBlank()) {
            log.error("短信模板未配置: type={}", type);
            throw new RuntimeException("短信服务未配置，请联系管理员");
        }

        // 调用阿里云短信
        try {
            Client client = buildClient();
            SendSmsRequest smsRequest = new SendSmsRequest()
                    .setPhoneNumbers(phone)
                    .setSignName(signName)
                    .setTemplateCode(templateCode)
                    .setTemplateParam("{\"code\":\"" + code + "\"}");

            SendSmsResponse response = client.sendSms(smsRequest);
            String resultCode = response.getBody().getCode();

            if (!"OK".equals(resultCode)) {
                log.error("阿里云短信发送失败: phone={}, type={}, code={}, message={}",
                          phone, type, resultCode, response.getBody().getMessage());
                throw new RuntimeException("短信发送失败，请稍后重试");
            }

            log.info("短信验证码发送成功: phone={}, type={}, template={}", phone, type, templateCode);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("短信服务异常: {}", e.getMessage(), e);
            throw new RuntimeException("短信服务异常，请稍后重试");
        }
    }

    /**
     * 验证验证码（验证成功后删除，防止重复使用）
     * 开发模式下额外接受固定码 000000
     * @return true = 验证通过
     */
    public boolean verifyCode(String phone, String inputCode) {
        // 开发模式：接受固定码 000000（方便调试）
        if (isDevMode() && "000000".equals(inputCode)) {
            log.warn("【开发模式】使用固定验证码 000000 登录: phone={}", phone);
            return true;
        }

        String redisKey = REDIS_KEY_PREFIX + phone;
        String storedCode = redisTemplate.opsForValue().get(redisKey);

        if (storedCode == null) {
            return false; // 已过期或未发送
        }
        if (!storedCode.equals(inputCode)) {
            return false; // 验证码错误
        }

        // 验证成功，立即删除（一次性使用）
        redisTemplate.delete(redisKey);
        return true;
    }

    // ─── 工具方法 ────────────────────────────────────────────

    /** 开发模式判断：阿里云凭证为占位符时视为未配置 */
    private boolean isDevMode() {
        return accessKeyId == null || accessKeyId.startsWith("your_")
            || accessKeySecret == null || accessKeySecret.startsWith("your_");
    }

    private String generateCode() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private Client buildClient() throws Exception {
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret);
        config.endpoint = "dysmsapi.aliyuncs.com";
        return new Client(config);
    }
}
