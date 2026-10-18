package xyz.erupt.sms.proxy;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import xyz.erupt.annotation.fun.DataProxy;
import xyz.erupt.core.exception.EruptWebApiRuntimeException;
import xyz.erupt.core.i18n.I18nTranslate;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.sms.core.SmsChannels;
import xyz.erupt.sms.model.SmsChannel;

/**
 * Keeps the SMS4J registry in step with the channel rows.
 *
 * @author YuePeng
 */
@Component
public class SmsChannelDataProxy implements DataProxy<SmsChannel> {

    private final EruptDao eruptDao;

    private final SmsChannels channels;

    public SmsChannelDataProxy(EruptDao eruptDao, SmsChannels channels) {
        this.eruptDao = eruptDao;
        this.channels = channels;
    }

    @Override
    public void beforeAdd(SmsChannel channel) {
        if (!SmsChannels.known(channel.getSupplier())) throw new EruptWebApiRuntimeException(I18nTranslate.$translate("sms.supplier_unknown"));
    }

    @Override
    public void beforeUpdate(SmsChannel channel) {
        // A password field comes back empty when left alone
        if (StringUtils.isBlank(channel.getAccessKeySecret())) channel.setAccessKeySecret(eruptDao.find(SmsChannel.class, channel.getId()).getAccessKeySecret());
    }

    @Override
    public void afterAdd(SmsChannel channel) {
        this.afterUpdate(channel);
    }

    @Override
    public void afterUpdate(SmsChannel channel) {
        if (Boolean.TRUE.equals(channel.getEnabled())) channels.register(channel);
        else channels.unregister(channel.getId());
        // only one default: the newest pick wins
        if (Boolean.TRUE.equals(channel.getIsDefault())) {
            for (SmsChannel other : eruptDao.lambdaQuery(SmsChannel.class).eq(SmsChannel::getIsDefault, true).ne(SmsChannel::getId, channel.getId()).list()) {
                other.setIsDefault(false);
                eruptDao.merge(other);
            }
        }
    }

    @Override
    public void afterDelete(SmsChannel channel) {
        channels.unregister(channel.getId());
    }

}
