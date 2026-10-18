package xyz.erupt.sms.core;

import org.springframework.stereotype.Component;
import xyz.erupt.core.scope.EruptScopes;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.sms.model.SmsChannel;

/**
 * The channel of the current request: the one picked in the workspace, already verified by erupt.
 *
 * @author YuePeng
 */
@Component
public class SmsContext {

    private final EruptDao eruptDao;

    public SmsContext(EruptDao eruptDao) {
        this.eruptDao = eruptDao;
    }

    public Long channelId() {
        return EruptScopes.get(SmsScope.class).getChannel();
    }

    public SmsChannel channel() {
        return eruptDao.find(SmsChannel.class, this.channelId());
    }

}
