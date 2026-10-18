package xyz.erupt.sms.proxy;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import xyz.erupt.annotation.fun.DataProxy;
import xyz.erupt.core.exception.EruptWebApiRuntimeException;
import xyz.erupt.core.i18n.I18nTranslate;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.sms.model.SmsChannel;
import xyz.erupt.sms.model.SmsTemplate;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A template left without parameters takes them from its content, in order of appearance.
 *
 * @author YuePeng
 */
@Component
public class SmsTemplateDataProxy implements DataProxy<SmsTemplate> {

    private final EruptDao eruptDao;

    public SmsTemplateDataProxy(EruptDao eruptDao) {
        this.eruptDao = eruptDao;
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{\\s*([A-Za-z0-9_]+)\\s*}");

    @Override
    public void beforeAdd(SmsTemplate template) {
        this.fill(template);
    }

    @Override
    public void beforeUpdate(SmsTemplate template) {
        this.fill(template);
    }

    private void fill(SmsTemplate template) {
        long same = eruptDao.lambdaQuery(SmsTemplate.class)
                .with(SmsTemplate::getChannel).eq(SmsChannel::getId, template.getChannel().getId()).with()
                .eq(SmsTemplate::getCode, template.getCode()).ne(null != template.getId(), SmsTemplate::getId, template.getId()).count();
        if (same > 0) throw new EruptWebApiRuntimeException(I18nTranslate.$translate("sms.code_exists") + " → " + template.getCode());
        if (StringUtils.isNotBlank(template.getParams()) || null == template.getContent()) return;
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(template.getContent());
        while (matcher.find()) names.add(matcher.group(1));
        template.setParams(names.isEmpty() ? null : String.join(",", names));
    }

}
