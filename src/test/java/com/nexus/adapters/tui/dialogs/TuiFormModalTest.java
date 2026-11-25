package com.nexus.adapters.tui.dialogs;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TuiFormModalTest {

    @Test
    @DisplayName("TuiFormModal supports text typing, cursor navigation, and backspace")
    void testTextInput() {
        TuiFormModal modal = new TuiFormModal("Test Modal");
        modal.addField(TuiFormModal.FormField.text("name", "Name", "abc"));

        // Append character 'd'
        modal.handleInput(new KeyStroke('d', false, false));
        assertThat(modal.getValue("name")).isEqualTo("abcd");

        // Backspace
        modal.handleInput(new KeyStroke(KeyType.Backspace));
        assertThat(modal.getValue("name")).isEqualTo("abc");

        // Cursor navigation (Home, insert 'z')
        modal.handleInput(new KeyStroke(KeyType.Home));
        modal.handleInput(new KeyStroke('z', false, false));
        assertThat(modal.getValue("name")).isEqualTo("zabc");
    }

    @Test
    @DisplayName("TuiFormModal supports option cycling with Space and ArrowRight/Left")
    void testOptionCycling() {
        TuiFormModal modal = new TuiFormModal("Option Modal");
        modal.addField(TuiFormModal.FormField.options("scope", "Scope", List.of("USER", "SYSTEM"), 0));

        assertThat(modal.getValue("scope")).isEqualTo("USER");

        // Space cycles option forward
        modal.handleInput(new KeyStroke(' ', false, false));
        assertThat(modal.getValue("scope")).isEqualTo("SYSTEM");

        // ArrowRight cycles option forward
        modal.handleInput(new KeyStroke(KeyType.ArrowRight));
        assertThat(modal.getValue("scope")).isEqualTo("USER");

        // ArrowLeft cycles option backward
        modal.handleInput(new KeyStroke(KeyType.ArrowLeft));
        assertThat(modal.getValue("scope")).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("TuiFormModal navigates fields with Tab and executes submit and cancel callbacks")
    void testNavigationAndCallbacks() {
        AtomicBoolean submitted = new AtomicBoolean(false);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        TuiFormModal modal = new TuiFormModal("Multi Modal");
        modal.addField(TuiFormModal.FormField.text("f1", "Field 1", "val1"));
        modal.addField(TuiFormModal.FormField.text("f2", "Field 2", "val2"));

        modal.setOnSubmit(m -> submitted.set(true));
        modal.setOnCancel(() -> cancelled.set(true));

        assertThat(modal.getFocusedFieldIndex()).isEqualTo(0);

        // Tab moves to field 1
        modal.handleInput(new KeyStroke(KeyType.Tab));
        assertThat(modal.getFocusedFieldIndex()).isEqualTo(1);

        // Enter submits form
        modal.handleInput(new KeyStroke(KeyType.Enter));
        assertThat(submitted.get()).isTrue();

        // ESC cancels form
        modal.handleInput(new KeyStroke(KeyType.Escape));
        assertThat(cancelled.get()).isTrue();
    }

    @Test
    @DisplayName("Renders modal dialog on virtual terminal without error")
    void testRender() throws Exception {
        TuiFormModal modal = new TuiFormModal("Render Test");
        modal.addField(TuiFormModal.FormField.text("name", "Name", "test-val"));
        modal.addField(TuiFormModal.FormField.options("scope", "Scope", List.of("USER", "SYSTEM"), 0));
        modal.setErrorMessage("Sample error display");

        DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(new TerminalSize(100, 30));
        TerminalScreen screen = new TerminalScreen(terminal);
        screen.startScreen();
        TextGraphics tg = screen.newTextGraphics();

        modal.render(tg, 100, 30);

        screen.refresh();
        screen.stopScreen();
    }
}
