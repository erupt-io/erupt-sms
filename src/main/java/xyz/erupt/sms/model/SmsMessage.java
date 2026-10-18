package xyz.erupt.sms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import xyz.erupt.annotation.Erupt;
import xyz.erupt.annotation.EruptField;
import xyz.erupt.annotation.EruptScope;
import xyz.erupt.annotation.config.QueryExpression;
import xyz.erupt.annotation.sub_erupt.Power;
import xyz.erupt.annotation.sub_erupt.RowOperation;
import xyz.erupt.annotation.sub_field.Edit;
import xyz.erupt.annotation.sub_field.EditType;
import xyz.erupt.annotation.sub_field.View;
import xyz.erupt.annotation.sub_field.sub_edit.ReferenceTableType;
import xyz.erupt.annotation.sub_field.sub_edit.Search;
import xyz.erupt.jpa.model.MetaModel;
import xyz.erupt.sms.core.SmsScope;
import xyz.erupt.sms.handler.SmsMessageHandler;
import xyz.erupt.sms.proxy.SmsMessageDataProxy;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * A message: saving it sends it, to one phone or many, and the row keeps the outcome.
 *
 * @author YuePeng
 */
@Getter
@Setter
@Entity
@Table(name = "e_sms_message")
@EruptScope(SmsScope.class)
@Erupt(name = "Messages", dataProxy = SmsMessageDataProxy.class, power = @Power(edit = false), orderBy = "sendTime desc",
        rowOperation = @RowOperation(code = "resend", title = "Resend", icon = "fa fa-repeat", mode = RowOperation.Mode.SINGLE, operationHandler = SmsMessageHandler.class))
public class SmsMessage extends MetaModel {

    public static final String SENT = "SENT";

    public static final String FAILED = "FAILED";

    @ManyToOne
    @EruptField(views = @View(title = "Channel", column = "name"), edit = @Edit(title = "Channel", notNull = true, type = EditType.REFERENCE_TABLE,
            referenceTableType = @ReferenceTableType(label = "name")))
    private SmsChannel channel;

    @ManyToOne
    @EruptField(views = @View(title = "Template", column = "name"), edit = @Edit(title = "Template", notNull = true, type = EditType.REFERENCE_TABLE,
            referenceTableType = @ReferenceTableType(label = "name", dependField = "channel", dependColumn = "channel.id")))
    private SmsTemplate template;

    @Column(length = 4000)
    @EruptField(views = @View(title = "Phones"), edit = @Edit(title = "Phones", notNull = true, type = EditType.TEXTAREA, search = @Search(operator = QueryExpression.LIKE),
            desc = "One per line"))
    private String phones;

    @JdbcTypeCode(SqlTypes.JSON)
    @EruptField(views = @View(title = "Parameters"), edit = @Edit(title = "Parameters", type = EditType.KEY_VALUE, desc = "A value for each parameter of the template"))
    private Map<String, String> params;

    @EruptField(views = @View(title = "Content"))
    private String content;

    @EruptField(views = @View(title = "Status"), edit = @Edit(title = "Status", search = @Search))
    private String status;

    @Column(length = 2000)
    @EruptField(views = @View(title = "Vendor response"))
    private String response;

    @EruptField(views = @View(title = "Sent at", sortable = true))
    private LocalDateTime sendTime;

}
