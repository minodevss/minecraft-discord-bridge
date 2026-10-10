package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.commands.GameCommands;
import com.minecraftdiscordbridge.core.commands.SlashRouter;
import com.minecraftdiscordbridge.core.events.EventPosts;
import com.minecraftdiscordbridge.core.i18n.Replies;
import com.minecraftdiscordbridge.core.status.StatusBoard;

record Session(Replies replies, EventPosts events, StatusBoard status, SlashRouter slash, GameCommands commands) {}
