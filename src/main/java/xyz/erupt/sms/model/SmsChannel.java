package xyz.erupt.sms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.dromara.sms4j.comm.constant.SupplierConstant;
import xyz.erupt.annotation.Erupt;
import xyz.erupt.annotation.EruptField;
import xyz.erupt.annotation.config.QueryExpression;
import xyz.erupt.annotation.sub_field.Edit;
import xyz.erupt.annotation.sub_field.EditType;
import xyz.erupt.annotation.sub_field.View;
import xyz.erupt.annotation.sub_field.sub_edit.*;
import xyz.erupt.jpa.model.MetaModel;
import xyz.erupt.sms.proxy.SmsChannelDataProxy;

/**
 * A vendor account. The only page outside the workspace: it is what the workspace picks from.
 *
 * @author YuePeng
 */
@Getter
@Setter
@Entity
@Table(name = "e_sms_channel")
@Erupt(name = "Channels", dataProxy = SmsChannelDataProxy.class)
public class SmsChannel extends MetaModel {

    @EruptField(views = @View(title = "Name"), edit = @Edit(title = "Name", notNull = true, search = @Search(operator = QueryExpression.LIKE)))
    private String name;

    @EruptField(views = @View(title = "Vendor"), edit = @Edit(title = "Vendor", notNull = true, search = @Search, type = EditType.CHOICE, choiceType = @ChoiceType(vl = {
            @VL(value = SupplierConstant.ALIBABA, label = "Alibaba Cloud"), @VL(value = SupplierConstant.TENCENT, label = "Tencent Cloud"),
            @VL(value = SupplierConstant.HUAWEI, label = "Huawei Cloud"), @VL(value = SupplierConstant.BAIDU, label = "Baidu Cloud"),
            @VL(value = SupplierConstant.JDCLOUD, label = "JD Cloud"), @VL(value = SupplierConstant.CTYUN, label = "China Telecom Cloud"),
            @VL(value = SupplierConstant.QINIU, label = "Qiniu"), @VL(value = SupplierConstant.NETEASE, label = "NetEase Yunxin"),
            @VL(value = SupplierConstant.YUNPIAN, label = "Yunpian"), @VL(value = SupplierConstant.UNISMS, label = "UniSMS"),
            @VL(value = SupplierConstant.CLOOPEN, label = "Cloopen"), @VL(value = SupplierConstant.EMAY, label = "Emay"),
            @VL(value = SupplierConstant.ZHUTONG, label = "Zhutong"), @VL(value = SupplierConstant.LIANLU, label = "Lianlu"),
            @VL(value = SupplierConstant.DINGZHONG, label = "Dingzhong"), @VL(value = SupplierConstant.CHUANGLAN, label = "Chuanglan"),
            @VL(value = SupplierConstant.JIGUANG, label = "Jiguang"), @VL(value = SupplierConstant.BUDING_V2, label = "Buding"),
            @VL(value = SupplierConstant.MAS, label = "China Mobile MAS"), @VL(value = SupplierConstant.LUO_SI_MAO, label = "Luosimao"),
            @VL(value = SupplierConstant.MY_SUBMAIL, label = "Submail"), @VL(value = SupplierConstant.DAN_MI, label = "Danmi"),
            @VL(value = SupplierConstant.YIXINTONG, label = "Yixintong")
    })))
    private String supplier;

    @EruptField(views = @View(title = "Access key"), edit = @Edit(title = "Access key", notNull = true))
    private String accessKeyId;

    @EruptField(edit = @Edit(title = "Secret", notNull = true, type = EditType.PASSWORD))
    private String accessKeySecret;

    @EruptField(views = @View(title = "Signature"), edit = @Edit(title = "Signature", notNull = true, desc = "The approved signature shown at the front of every message"))
    private String signature;

    @EruptField(views = @View(title = "SDK app ID"), edit = @Edit(title = "SDK app ID", desc = "Tencent, Huawei and some others need it"))
    private String sdkAppId;

    @Column(length = 2000)
    @EruptField(edit = @Edit(title = "Vendor settings", type = EditType.TEXTAREA,
            desc = "One key=value per line onto the vendor's SMS4J config, such as regionId=cn-hangzhou or territory=ap-guangzhou"))
    private String extra;

    @EruptField(views = @View(title = "Codes per phone per day"), edit = @Edit(title = "Codes per phone per day", type = EditType.NUMBER, notNull = true))
    private Integer dailyCodeLimit = 10;

    @EruptField(views = @View(title = "Enabled"), edit = @Edit(title = "Enabled", notNull = true, search = @Search, boolType = @BoolType(trueText = "Enabled", falseText = "Disabled")))
    private Boolean enabled = true;

    // The channel code sends through when it names none; one per installation
    @EruptField(views = @View(title = "Default"), edit = @Edit(title = "Default", notNull = true, boolType = @BoolType(trueText = "Default", falseText = "Not default"),
            desc = "Used by EruptSms when no channel is named"))
    private Boolean isDefault = false;

}
