package xyz.erupt.sms;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import xyz.erupt.core.annotation.EruptScan;
import xyz.erupt.core.module.EruptModule;
import xyz.erupt.core.module.EruptModuleInvoke;
import xyz.erupt.core.module.MetaMenu;
import xyz.erupt.core.module.ModuleInfo;
import xyz.erupt.sms.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * SMS across vendors on erupt. Channels hold the vendor credentials, the workspace in the header
 * picks one, and templates, messages and verification codes follow it.
 *
 * @author YuePeng
 */
@Configuration
// The standalone launcher lives in this package too; a host application must not pick it up
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootApplication.class))
@EruptScan
@EntityScan
public class EruptSmsAutoConfiguration implements EruptModule {

    static {
        EruptModuleInvoke.addEruptModule(EruptSmsAutoConfiguration.class);
    }

    @Override
    public ModuleInfo info() {
        return ModuleInfo.builder().name("erupt-sms").description("SMS across vendors").build();
    }

    @Override
    public List<MetaMenu> initMenus() {
        List<MetaMenu> menus = new ArrayList<>();
        MetaMenu root = MetaMenu.createRootMenu("$sms", "SMS", "fa fa-commenting", 55);
        menus.add(root);
        int sort = 0;
        for (Class<?> model : List.of(SmsChannel.class, SmsTemplate.class, SmsMessage.class, SmsCode.class, SmsBlacklist.class)) {
            menus.add(MetaMenu.createEruptClassMenu(model, root, sort += 10));
        }
        return menus;
    }

}
