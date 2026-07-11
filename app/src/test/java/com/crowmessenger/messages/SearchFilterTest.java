package com.crowmessenger.messages;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SearchFilterTest {
    @Test
    public void matches_separatesPeopleTextAndPictures() {
        assertTrue(SearchFilter.PEOPLE.matches("dave", "15551234567", "Dave", "Lunch", false));
        assertFalse(SearchFilter.PEOPLE.matches("lunch", "15551234567", "Dave", "Lunch", false));
        assertTrue(SearchFilter.MESSAGE_TEXT.matches("lunch", "15551234567", "Dave", "Lunch", false));
        assertFalse(SearchFilter.PICTURES.matches("", "15551234567", "Dave", "", false));
        assertTrue(SearchFilter.PICTURES.matches("", "15551234567", "Dave", "", true));
    }
}
