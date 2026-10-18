package xyz.erupt.sms.core;

import lombok.Getter;
import lombok.Setter;
import xyz.erupt.annotation.Erupt;
import xyz.erupt.annotation.EruptField;
import xyz.erupt.annotation.sub_field.Edit;
import xyz.erupt.annotation.sub_field.EditType;
import xyz.erupt.annotation.sub_field.sub_edit.ChoiceType;

/**
 * The workspace every SMS page follows: one channel, a vendor account. Picked once in the page
 * header; templates, messages and codes are bound to it by their {@code channel} field.
 *
 * @author YuePeng
 */
@Getter
@Setter
@Erupt(name = "Workspace", primaryKeyCol = "channel")
public class SmsScope {

    @EruptField(edit = @Edit(title = "Channel", type = EditType.CHOICE, notNull = true, choiceType = @ChoiceType(fetchHandler = SmsChannelFetchHandler.class)))
    private Long channel;

}
