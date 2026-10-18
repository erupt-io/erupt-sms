package xyz.erupt.sms.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import xyz.erupt.annotation.Erupt;
import xyz.erupt.annotation.EruptField;
import xyz.erupt.annotation.config.QueryExpression;
import xyz.erupt.annotation.sub_field.Edit;
import xyz.erupt.annotation.sub_field.View;
import xyz.erupt.annotation.sub_field.sub_edit.Search;
import xyz.erupt.jpa.model.MetaModel;

/**
 * Phones never sent to, on any channel.
 *
 * @author YuePeng
 */
@Getter
@Setter
@Entity
@Table(name = "e_sms_blacklist", uniqueConstraints = @UniqueConstraint(columnNames = "phone"))
@Erupt(name = "Blacklist")
public class SmsBlacklist extends MetaModel {

    @EruptField(views = @View(title = "Phone"), edit = @Edit(title = "Phone", notNull = true, search = @Search(operator = QueryExpression.LIKE)))
    private String phone;

    @EruptField(views = @View(title = "Reason"), edit = @Edit(title = "Reason"))
    private String reason;

}
