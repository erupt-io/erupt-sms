package xyz.erupt.sms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import xyz.erupt.annotation.Erupt;
import xyz.erupt.annotation.EruptField;
import xyz.erupt.annotation.EruptScope;
import xyz.erupt.annotation.config.QueryExpression;
import xyz.erupt.annotation.sub_field.Edit;
import xyz.erupt.annotation.sub_field.EditType;
import xyz.erupt.annotation.sub_field.View;
import xyz.erupt.annotation.sub_field.sub_edit.*;
import xyz.erupt.jpa.model.MetaModel;
import xyz.erupt.sms.core.SmsScope;
import xyz.erupt.sms.proxy.SmsTemplateDataProxy;

/**
 * A template approved at the vendor, with the parameter names in the order the vendor expects.
 *
 * @author YuePeng
 */
@Getter
@Setter
@Entity
@Table(name = "e_sms_template")
@EruptScope(SmsScope.class)
@Erupt(name = "Templates", dataProxy = SmsTemplateDataProxy.class)
public class SmsTemplate extends MetaModel {

    public static final String CODE = "CODE";

    public static final String NOTICE = "NOTICE";

    public static final String MARKETING = "MARKETING";

    @ManyToOne
    @EruptField(views = @View(title = "Channel", column = "name"), edit = @Edit(title = "Channel", notNull = true, type = EditType.REFERENCE_TABLE,
            referenceTableType = @ReferenceTableType(label = "name")))
    private SmsChannel channel;

    // What code refers to: ORDER_SHIPPED, LOGIN_CODE; unique within the channel
    @EruptField(views = @View(title = "Code"), edit = @Edit(title = "Code", notNull = true, search = @Search(operator = QueryExpression.LIKE),
            inputType = @InputType(regex = "^[A-Z][A-Z0-9_]*$"), desc = "Upper case letters, digits and underscores; the name the application sends by"))
    private String code;

    @EruptField(views = @View(title = "Name"), edit = @Edit(title = "Name", notNull = true, search = @Search(operator = QueryExpression.LIKE)))
    private String name;

    @EruptField(views = @View(title = "Vendor template ID"), edit = @Edit(title = "Vendor template ID", notNull = true, desc = "As shown in the vendor console, such as SMS_123456"))
    private String templateId;

    @EruptField(views = @View(title = "Kind"), edit = @Edit(title = "Kind", notNull = true, search = @Search, type = EditType.CHOICE, choiceType = @ChoiceType(vl = {
            @VL(value = CODE, label = "Verification code"), @VL(value = NOTICE, label = "Notification"), @VL(value = MARKETING, label = "Marketing")
    })))
    private String kind = NOTICE;

    @EruptField(views = @View(title = "Enabled"), edit = @Edit(title = "Enabled", notNull = true, search = @Search, boolType = @BoolType(trueText = "Enabled", falseText = "Disabled")))
    private Boolean enabled = true;

    // Plain text as the vendor approved it: a short textarea, capped at the longest a vendor accepts
    @Column(length = 500)
    @EruptField(views = @View(title = "Content"), edit = @Edit(title = "Content", notNull = true, type = EditType.TEXTAREA,
            textareaType = @TextareaType(length = 500, minRows = 3, maxRows = 6),
            desc = "The vendor's text with ${name} placeholders; kept here to show what was sent"))
    private String content;

    // Stored as "name,order": the names in the vendor's order, which is what positional vendors read
    @EruptField(views = @View(title = "Parameters"), edit = @Edit(title = "Parameters", type = EditType.TAGS, tagsType = @TagsType(joinSeparator = ","),
            desc = "The ${name} placeholders of the content, in the vendor's order; filled in from the content when left empty. A code template uses \"code\""))
    private String params;

}
