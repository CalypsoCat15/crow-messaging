package com.crowmessenger.messages;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class AddressUtilTest {
    @Test
    public void containsMatchingPhoneValue_matchesFormattedSameNumber() {
        Set<String> values = new HashSet<>();
        values.add("+1 (555) 123-4567");

        assertTrue(AddressUtil.containsMatchingPhoneValue(values, "5551234567"));
    }

    @Test
    public void containsMatchingPhoneValue_doesNotMatchParticipantInsideGroupAddress() {
        String group = LocalMmsStore.conversationAddress("", List.of(
                "15550001111",
                "15551234567",
                "15557654321"
        ));
        Set<String> values = new HashSet<>();
        values.add(group);

        assertFalse(AddressUtil.containsMatchingPhoneValue(values, "15557654321"));
    }

    @Test
    public void removeMatchingPhoneValues_removesOnlySamePhoneNumber() {
        Set<String> values = new HashSet<>();
        values.add("+1 (555) 123-4567");
        values.add("15557654321");

        AddressUtil.removeMatchingPhoneValues(values, "5551234567");

        assertFalse(values.contains("+1 (555) 123-4567"));
        assertTrue(values.contains("15557654321"));
    }
}
