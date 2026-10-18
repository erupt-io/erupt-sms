package xyz.erupt.sms.core;

import org.springframework.stereotype.Component;
import xyz.erupt.annotation.fun.ChoiceFetchHandler;
import xyz.erupt.annotation.fun.VLModel;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.sms.model.SmsChannel;

import java.util.List;

/**
 * @author YuePeng
 */
@Component
public class SmsChannelFetchHandler implements ChoiceFetchHandler<SmsScope> {

    private final EruptDao eruptDao;

    public SmsChannelFetchHandler(EruptDao eruptDao) {
        this.eruptDao = eruptDao;
    }

    @Override
    public List<VLModel> fetch(String[] params) {
        return eruptDao.lambdaQuery(SmsChannel.class).orderBy(SmsChannel::getName).list().stream()
                .map(it -> new VLModel(String.valueOf(it.getId()), it.getName() + " (" + it.getSupplier() + ")")).toList();
    }

}
