package com.example.application.views;

import com.example.application.chat.ChatService;
import com.example.application.chat.Message;
import com.example.application.util.LimitedSortedAppendOnlyList;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import reactor.core.Disposable;
import java.util.Comparator;
import java.util.List;

@Route(value = "channel", layout = MainLayout.class)
@PermitAll
public class ChannelView extends VerticalLayout implements HasUrlParameter<String>, HasDynamicTitle {

    private final ChatService chatService;
    private final MessageList messageList;
    private String channelId;
    private static final int HISTORY_SIZE = 20;
    private final LimitedSortedAppendOnlyList<Message> receivedMessages;
    private final String currentUserName;
    private String channelName;

    public ChannelView(ChatService chatService, AuthenticationContext authenticationContext) {
        this.currentUserName = authenticationContext.getPrincipalName().orElseThrow();
        this.chatService = chatService;
        receivedMessages = new LimitedSortedAppendOnlyList<>(
                HISTORY_SIZE,
                Comparator.comparing(Message::sequenceNumber)
        );
        setSizeFull();

        this.messageList = new MessageList();
        messageList.addClassNames(LumoUtility.Border.ALL);
        messageList.setSizeFull();
        add(messageList);

        var messageInput = new MessageInput(submitEvent -> sendMessage(submitEvent.getValue()));
        messageInput.setWidthFull();
        add(messageInput);
    }

    @Override
    public void setParameter(BeforeEvent event, String channelId) {
        chatService.channel(channelId).ifPresentOrElse(
                channel -> this.channelName = channel.name(),
                () -> event.forwardTo(LobbyView.class)
        );
        this.channelId = channelId;
    }
    private void sendMessage(String message){
        if(!message.isBlank()){
            chatService.postMessage( channelId,message);
        }
    }
    private MessageListItem createMessageListItem(Message message) {
        var item = new MessageListItem(
                message.message(),
                message.timestamp(),
                message.author()
        );
        item.setUserColorIndex(Math.abs(message.author().hashCode() % 7));
        item.addClassNames(LumoUtility.Margin.SMALL, LumoUtility.BorderRadius.MEDIUM);
        if (message.author().equals(currentUserName)) {
            item.addClassNames(LumoUtility.Background.CONTRAST_5);
        }
        return item;
    }

    private void receiveMessages(List<Message> incoming) {
        getUI().ifPresent(ui -> ui.access(() -> {
            receivedMessages.addAll(incoming);
            messageList.setItems(receivedMessages.stream()
                    .map(this::createMessageListItem)
                    .toList());
        }));
    }

    private Disposable subscribe() {
        var subscription = chatService
                .liveMessages(channelId)
                .subscribe(this::receiveMessages);
        var lastSeenMessageId = receivedMessages.getLast()
                .map(Message::messageId).orElse(null);

        receiveMessages(chatService.messageHistory(
                channelId,
                HISTORY_SIZE,
                lastSeenMessageId
        ));
        return subscription;
    }
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        var subscription = subscribe();
        addDetachListener(event -> subscription.dispose());
    }

    @Override
    public String getPageTitle() {
        return channelName;
    }
}
