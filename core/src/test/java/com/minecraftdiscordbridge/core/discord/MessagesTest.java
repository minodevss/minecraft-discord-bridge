package com.minecraftdiscordbridge.core.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.Test;

class MessagesTest {
    @Test
    void allowsOnlyTheListedUsersToBePinged() {
        DataObject mentions =
                Messages.create("hi <@5> @everyone", Set.of(5L)).toData().getObject("allowed_mentions");

        assertThat(mentions.getArray("parse").length()).isZero();
        assertThat(mentions.getArray("users").getString(0)).isEqualTo("5");
        assertThat(mentions.hasKey("roles")).isFalse();
        assertThat(mentions.getBoolean("replied_user")).isFalse();
    }

    @Test
    void allowsNoPingsByDefault() {
        DataObject mentions = Messages.create("@here").toData().getObject("allowed_mentions");

        assertThat(mentions.getArray("parse").length()).isZero();
        assertThat(mentions.hasKey("users")).isFalse();
    }

    @Test
    void setsMentionsOnEveryEdit() {
        DataObject edit = Messages.edit("@everyone").toData();

        assertThat(edit.getObject("allowed_mentions").getArray("parse").length())
                .isZero();
    }

    @Test
    void shortensContentToTwoThousandCharacters() {
        assertThat(Messages.create("z".repeat(2100)).getContent()).hasSize(2000);
        assertThat(Messages.edit("z".repeat(2100)).getContent()).hasSize(2000);
    }
}
