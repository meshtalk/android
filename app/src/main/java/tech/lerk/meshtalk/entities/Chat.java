package tech.lerk.meshtalk.entities;

import java.util.List;
import java.util.UUID;

public class Chat {
    private UUID id;
    private String title;
    private List<UUID> messages;
    private List<UUID> participants;
}
