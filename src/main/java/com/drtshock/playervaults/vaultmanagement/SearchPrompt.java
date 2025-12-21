package com.drtshock.playervaults.vaultmanagement;

import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.entity.Player;

public class SearchPrompt extends StringPrompt {

    @Override
    public String getPromptText(ConversationContext context) {
        return "§eType your search query in chat...";
    }

    @Override
    public Prompt acceptInput(ConversationContext context, String input) {
        if (input != null && !input.isEmpty()) {
            Player player = (Player) context.getForWhom();
            // Search launch
            VaultSearcher.search(player, input);
        }
        return Prompt.END_OF_CONVERSATION;
    }
}
