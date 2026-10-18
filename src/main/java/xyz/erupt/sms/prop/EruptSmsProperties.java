package xyz.erupt.sms.prop;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * <pre>
 * erupt:
 *   sms:
 *     code-length: 6        # digits in a verification code
 *     code-ttl-seconds: 300 # how long a code stays valid
 *     resend-seconds: 60    # the wait before the same phone may ask for another code
 * </pre>
 *
 * @author YuePeng
 */
@Getter
@Setter
@Component
@ConfigurationProperties("erupt.sms")
public class EruptSmsProperties {

    private int codeLength = 6;

    private int codeTtlSeconds = 300;

    private int resendSeconds = 60;

}
