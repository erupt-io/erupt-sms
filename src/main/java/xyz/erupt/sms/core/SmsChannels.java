package xyz.erupt.sms.core;

import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.universal.SupplierConfig;
import org.dromara.sms4j.javase.config.SEInitializer;
import org.dromara.sms4j.provider.config.BaseConfig;
import org.dromara.sms4j.provider.config.SmsConfig;
import org.dromara.sms4j.provider.factory.BaseProviderFactory;
import org.dromara.sms4j.provider.factory.ProviderFactoryHolder;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import xyz.erupt.core.exception.EruptWebApiRuntimeException;
import xyz.erupt.core.i18n.I18nTranslate;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.sms.model.SmsChannel;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One SMS4J {@link SmsBlend} per channel row, built from the row rather than from a config file,
 * so channels are added and changed at runtime.
 *
 * @author YuePeng
 */
@Component
public class SmsChannels {

    private final Map<Long, SmsBlend> blends = new ConcurrentHashMap<>();

    private final EruptDao eruptDao;

    public SmsChannels(EruptDao eruptDao) {
        this.eruptDao = eruptDao;
    }

    // SMS4J's executor and store are set up once; the suppliers come from rows, so none here
    @PostConstruct
    public void init() {
        SEInitializer.initializer().fromConfig(new SmsConfig(), List.of());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        eruptDao.lambdaQuery(SmsChannel.class).eq(SmsChannel::getEnabled, true).list().forEach(this::register);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void register(SmsChannel channel) {
        BaseProviderFactory factory = ProviderFactoryHolder.requireForSupplier(channel.getSupplier());
        if (null == factory) throw new EruptWebApiRuntimeException(I18nTranslate.$translate("sms.supplier_unknown") + " → " + channel.getSupplier());
        BaseConfig config;
        try {
            config = (BaseConfig) factory.getConfigClass().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new EruptWebApiRuntimeException(e.getMessage());
        }
        config.setConfigId(String.valueOf(channel.getId()));
        config.setAccessKeyId(channel.getAccessKeyId());
        config.setAccessKeySecret(channel.getAccessKeySecret());
        config.setSignature(channel.getSignature());
        config.setSdkAppId(channel.getSdkAppId());
        // vendor-specific settings, "regionId=cn-hangzhou" a line, go onto the vendor's config class
        BeanWrapperImpl wrapper = new BeanWrapperImpl(config);
        for (String line : SmsText.lines(channel.getExtra())) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim();
            if (!wrapper.isWritableProperty(key)) throw new EruptWebApiRuntimeException(I18nTranslate.$translate("sms.extra_unknown") + " → " + key);
            wrapper.setPropertyValue(key, line.substring(eq + 1).trim());
        }
        blends.put(channel.getId(), (SmsBlend) factory.createSms((SupplierConfig) config));
    }

    public void unregister(Long channelId) {
        blends.remove(channelId);
    }

    // For tests and for custom vendors: any SmsBlend can stand in for a channel
    public void put(Long channelId, SmsBlend blend) {
        blends.put(channelId, blend);
    }

    public SmsBlend get(Long channelId) {
        SmsBlend blend = null == channelId ? null : blends.get(channelId);
        if (null == blend) throw new EruptWebApiRuntimeException(I18nTranslate.$translate("sms.channel_unknown") + " → " + channelId);
        return blend;
    }

    public static boolean known(String supplier) {
        return StringUtils.isNotBlank(supplier) && null != ProviderFactoryHolder.requireForSupplier(supplier);
    }

}
