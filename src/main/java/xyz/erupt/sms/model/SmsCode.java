package xyz.erupt.sms.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import xyz.erupt.annotation.Erupt;
import xyz.erupt.annotation.EruptField;
import xyz.erupt.annotation.EruptScope;
import xyz.erupt.annotation.config.QueryExpression;
import xyz.erupt.annotation.sub_erupt.Power;
import xyz.erupt.annotation.sub_field.Edit;
import xyz.erupt.annotation.sub_field.EditType;
import xyz.erupt.annotation.sub_field.View;
import xyz.erupt.annotation.sub_field.sub_edit.BoolType;
import xyz.erupt.annotation.sub_field.sub_edit.ReferenceTableType;
import xyz.erupt.annotation.sub_field.sub_edit.Search;
import xyz.erupt.jpa.model.BaseModel;
import xyz.erupt.sms.core.SmsScope;

import java.time.LocalDateTime;

/**
 * A verification code issued through {@code POST /sms/code/{channel}/send} and consumed through
 * {@code POST /sms/code/{channel}/verify}.
 *
 * @author YuePeng
 */
@Getter
@Setter
@Entity
@Table(name = "e_sms_code", indexes = @Index(columnList = "phone"))
@EruptScope(SmsScope.class)
@Erupt(name = "Verification codes", power = @Power(add = false, edit = false), orderBy = "sendTime desc")
public class SmsCode extends BaseModel {

    @ManyToOne
    @EruptField(views = @View(title = "Channel", column = "name"), edit = @Edit(title = "Channel", type = EditType.REFERENCE_TABLE,
            referenceTableType = @ReferenceTableType(label = "name")))
    private SmsChannel channel;

    @EruptField(views = @View(title = "Phone"), edit = @Edit(title = "Phone", search = @Search(operator = QueryExpression.LIKE)))
    private String phone;

    @EruptField(views = @View(title = "Verification code"))
    private String code;

    @EruptField(views = @View(title = "Expires at"))
    private LocalDateTime expireAt;

    @EruptField(views = @View(title = "Used"), edit = @Edit(title = "Used", search = @Search, boolType = @BoolType(trueText = "Used", falseText = "Unused")))
    private Boolean used = false;

    @EruptField(views = @View(title = "Sent"), edit = @Edit(title = "Sent", search = @Search, boolType = @BoolType(trueText = "Sent", falseText = "Failed")))
    private Boolean sent;

    @EruptField(views = @View(title = "Sent at", sortable = true))
    private LocalDateTime sendTime;

}
