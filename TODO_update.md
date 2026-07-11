# TODO: Rounded Crow-Style Menus

Status: Implemented.

## Goal

Make the three-dot menus feel like the newer Conversation Details screen:

- Rounded/pill-shaped instead of default square Android popups.
- Pure black and dark charcoal surfaces with mint action buttons.
- Consistent spacing, typography, and touch targets across the inbox and individual text thread menus.
- Keep the app feeling polished, simple, and Samsung Messages-like.

## Implementation

1. Replace the default `AlertDialog` list menus used by:
   - Inbox three-dot menu.
   - Individual conversation three-dot menu.

2. Add a shared helper in `MainActivity.java`, for example:

   ```java
   private void showCrowMenu(String title, List<String> options, MenuChoiceHandler handler)
   ```

   This helper should:
   - Create a vertical `LinearLayout`.
   - Use `roundedBackground(CHAT_HEADER, 28)` for the menu panel.
   - Add a centered title at the top.
   - Add one rounded mint button per menu option.
   - Add a darker rounded `Cancel` button at the bottom.
   - Set the dialog window background to transparent so only the rounded menu is visible.

3. Add a small callback interface:

   ```java
   private interface MenuChoiceHandler {
       void onChoice(String choice);
   }
   ```

4. Update `showInboxMenu()` to call the shared helper with options like:
   - Spam & blocked
   - Spam filter rules
   - MMS status
   - Make default SMS app, only if the app is not already default

5. Update `showConversationMenu(Conversation conversation)` to call the shared helper with options like:
   - Conversation details
   - Search conversation
   - Custom notification sound
   - Mute conversation / Unmute conversation
   - Block / Unblock
   - Delete conversation

6. Keep behavior exactly the same after a menu item is tapped. Only the menu styling should change.

## Visual Notes

- The menu should look soft and intentional, not like a plain system popup.
- Buttons should match the app's rounded mint style.
- The inbox menu and conversation menu should feel like the same design family.
- Avoid square corners unless Android forces them somewhere outside the app's control.

## Testing

After implementation:

1. Build the app.
2. Install it on the phone.
3. Open the inbox and tap the top-right three-dot button.
4. Open a conversation and tap the top-right three-dot button.
5. Confirm every option still works.
6. Confirm tapping outside or pressing Cancel closes the menu normally.

# TODO: Adjustable Text Size

Status: Implemented as a menu setting. Pinch-to-zoom is still optional for later.

## Goal

Add an easy way for users with different eyesight needs to make message text bigger or smaller.

This would be especially helpful for people like parents or older users who may want larger text than the default app design.

## Preferred Behavior

- Let the user adjust the text size for message bubbles and inbox previews.
- The app should remember the selected size after closing and reopening.
- The default size should stay close to what the app uses now.
- Text should never get so large that it breaks the layout or hides important buttons.

## Possible Implementation Options

Option 1: Settings menu control

- Add a `Text size` option under the inbox three-dot menu.
- Show choices like:
  - Small
  - Normal
  - Large
  - Extra large
- This is the safest and clearest option.

Option 2: Pinch-to-zoom inside conversations

- Allow pinching on the conversation screen to make message text larger or smaller.
- Save the updated size after the pinch gesture.
- This feels natural, but it is more complex and easier to accidentally trigger.

## Recommended Approach

Start with the settings menu control first because it is simpler, easier to understand, and less likely to cause accidental changes.

After that works well, consider adding pinch-to-zoom as a bonus shortcut.
