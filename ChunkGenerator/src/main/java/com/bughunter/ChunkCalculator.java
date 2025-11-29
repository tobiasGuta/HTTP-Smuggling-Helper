package com.bughunter;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Range;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Implements BOTH ContextMenu (Right-click) and HttpHandler (Traffic Interceptor)
public class ChunkCalculator implements BurpExtension, ContextMenuItemsProvider, HttpHandler {

    private MontoyaApi api;
    private static final String MAGIC_HEADER = "X-Smuggle-Ignore";

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        api.extension().setName("Smuggling Helper (Auto-Bypass)");

        // 1. Register the Menu
        api.userInterface().registerContextMenuItemsProvider(this);

        // 2. Register the Traffic Listener (The "Driver")
        api.http().registerHttpHandler(this);

        api.logging().logToOutput("Loaded! 'Desync' payloads will now automatically bypass Burp's auto-fix.");
    }

    // ==========================================================
    // PART 1: The Context Menu (Putting the Post-It Note on)
    // ==========================================================
    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        if (event.messageEditorRequestResponse().isEmpty()) return null;

        MessageEditorHttpRequestResponse editor = event.messageEditorRequestResponse().get();
        Optional<Range> selection = editor.selectionOffsets();

        if (selection.isEmpty()) return null;

        // Menu 1: CL.TE
        JMenuItem chunkItem = new JMenuItem("Chunkify Selection (Hex / CL.TE)");
        chunkItem.addActionListener(l -> processSelection(editor, selection.get(), "HEX"));

        // Menu 2: H2.CL (True Length)
        JMenuItem clItem = new JMenuItem("Insert Content-Length (True Length)");
        clItem.addActionListener(l -> processSelection(editor, selection.get(), "TRUE_CL"));

        // Menu 3: H2.CL Desync (The Magic Bypass)
        JMenuItem desyncItem = new JMenuItem("Insert Desync Header (CL: 0 + Bypass)");
        desyncItem.addActionListener(l -> processSelection(editor, selection.get(), "ZERO_CL"));

        List<Component> menuList = new ArrayList<>();
        menuList.add(chunkItem);
        menuList.add(clItem);
        menuList.add(desyncItem);
        return menuList;
    }

    // ==========================================================
    // PART 2: The Traffic Handler (The Driver fixing the label)
    // ==========================================================
    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {

        // Check if our "Magic Header" exists
        if (requestToBeSent.hasHeader(MAGIC_HEADER)) {

            // 1. Remove the Magic Header (so the server doesn't see it)
            HttpRequest modifiedRequest = requestToBeSent.withRemovedHeader(MAGIC_HEADER);

            // 2. FORCE Content-Length to 0 (Overriding Burp's auto-fix)
            // We update the header if it exists, or add it if it's missing
            if (modifiedRequest.hasHeader("Content-Length")) {
                modifiedRequest = modifiedRequest.withUpdatedHeader("Content-Length", "0");
            } else {
                modifiedRequest = modifiedRequest.withAddedHeader("Content-Length", "0");
            }

            // 3. Send the modified request
            return RequestToBeSentAction.continueWith(modifiedRequest);
        }

        return RequestToBeSentAction.continueWith(requestToBeSent);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        return ResponseReceivedAction.continueWith(responseReceived);
    }

    // ==========================================================
    // PART 3: The Helper Logic
    // ==========================================================
    private void processSelection(MessageEditorHttpRequestResponse editor, Range range, String mode) {
        try {
            HttpRequest currentRequest = editor.requestResponse().request();
            ByteArray fullRequestBytes = currentRequest.toByteArray();

            int start = range.startIndexInclusive();
            int end = range.endIndexExclusive();
            int length = end - start;

            if (length > 0) {
                String headerString = "";

                switch (mode) {
                    case "HEX":
                        headerString = Integer.toHexString(length) + "\r\n";
                        break;
                    case "TRUE_CL":
                        headerString = "Content-Length: " + length + "\r\n\r\n";
                        break;
                    case "ZERO_CL":
                        // We insert CL: 0 AND the Magic Header
                        headerString = "Content-Length: 0\r\n" + MAGIC_HEADER + ": true\r\n\r\n";
                        break;
                }

                ByteArray headerBytes = ByteArray.byteArray(headerString);
                ByteArray newBody = fullRequestBytes.subArray(0, start)
                        .withAppended(headerBytes)
                        .withAppended(fullRequestBytes.subArray(start, fullRequestBytes.length()));

                editor.setRequest(HttpRequest.httpRequest(newBody));
            }
        } catch (Exception e) {
            api.logging().logToError("Error: " + e.getMessage());
        }
    }
}