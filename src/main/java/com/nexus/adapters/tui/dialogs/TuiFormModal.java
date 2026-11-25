package com.nexus.adapters.tui.dialogs;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.nexus.adapters.tui.TuiDrawingHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reusable modal form dialog providing interactive keyboard input,
 * field navigation, option selectors, cursor movement, and validation.
 */
public class TuiFormModal {

    public enum FieldType {
        TEXT,
        OPTIONS
    }

    public static class FormField {
        private final String id;
        private final String label;
        private final FieldType type;
        private final List<String> options;
        private int selectedOptionIndex = 0;
        private final StringBuilder textValue = new StringBuilder();
        private int cursorPosition = 0;

        public FormField(String id, String label, FieldType type, List<String> options, String defaultText) {
            this.id = id;
            this.label = label;
            this.type = type;
            this.options = options != null ? new ArrayList<>(options) : List.of();
            if (defaultText != null) {
                this.textValue.append(defaultText);
                this.cursorPosition = this.textValue.length();
            }
        }

        public static FormField text(String id, String label, String defaultValue) {
            return new FormField(id, label, FieldType.TEXT, null, defaultValue);
        }

        public static FormField options(String id, String label, List<String> options, int defaultIndex) {
            FormField f = new FormField(id, label, FieldType.OPTIONS, options, null);
            f.selectedOptionIndex = Math.max(0, Math.min(defaultIndex, options.size() - 1));
            return f;
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        public FieldType getType() {
            return type;
        }

        public String getValue() {
            if (type == FieldType.OPTIONS) {
                return !options.isEmpty() ? options.get(selectedOptionIndex) : "";
            }
            return textValue.toString();
        }

        public void setValue(String value) {
            if (type == FieldType.OPTIONS) {
                int idx = options.indexOf(value);
                if (idx >= 0) selectedOptionIndex = idx;
            } else {
                textValue.setLength(0);
                if (value != null) {
                    textValue.append(value);
                }
                cursorPosition = textValue.length();
            }
        }

        public void cycleOption(int delta) {
            if (options.isEmpty()) return;
            selectedOptionIndex = (selectedOptionIndex + delta + options.size()) % options.size();
        }

        public void insertChar(char ch) {
            textValue.insert(cursorPosition, ch);
            cursorPosition++;
        }

        public void backspace() {
            if (cursorPosition > 0 && textValue.length() > 0) {
                textValue.deleteCharAt(cursorPosition - 1);
                cursorPosition--;
            }
        }

        public void delete() {
            if (cursorPosition < textValue.length()) {
                textValue.deleteCharAt(cursorPosition);
            }
        }

        public void moveCursor(int delta) {
            cursorPosition = Math.max(0, Math.min(textValue.length(), cursorPosition + delta));
        }
    }

    private final String title;
    private final List<FormField> fields = new ArrayList<>();
    private int focusedFieldIndex = 0;
    private String errorMessage = null;
    private Consumer<TuiFormModal> onSubmit;
    private Runnable onCancel;

    public TuiFormModal(String title) {
        this.title = title;
    }

    public TuiFormModal addField(FormField field) {
        this.fields.add(field);
        return this;
    }

    public List<FormField> getFields() {
        return fields;
    }

    public String getValue(String id) {
        for (FormField f : fields) {
            if (f.getId().equals(id)) {
                return f.getValue();
            }
        }
        return null;
    }

    public void setValue(String id, String val) {
        for (FormField f : fields) {
            if (f.getId().equals(id)) {
                f.setValue(val);
                break;
            }
        }
    }

    public void setOnSubmit(Consumer<TuiFormModal> onSubmit) {
        this.onSubmit = onSubmit;
    }

    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getFocusedFieldIndex() {
        return focusedFieldIndex;
    }

    public void setFocusedFieldIndex(int index) {
        if (!fields.isEmpty()) {
            this.focusedFieldIndex = Math.max(0, Math.min(index, fields.size() - 1));
        }
    }

    public boolean handleInput(KeyStroke key) {
        if (key == null) return false;

        KeyType type = key.getKeyType();
        Character ch = key.getCharacter();

        // Cancel dialog
        if (type == KeyType.Escape) {
            if (onCancel != null) onCancel.run();
            return true;
        }

        // Navigate fields
        if (type == KeyType.Tab || type == KeyType.ArrowDown) {
            if (!fields.isEmpty()) {
                focusedFieldIndex = (focusedFieldIndex + 1) % fields.size();
                errorMessage = null;
            }
            return true;
        } else if (type == KeyType.ReverseTab || type == KeyType.ArrowUp) {
            if (!fields.isEmpty()) {
                focusedFieldIndex = (focusedFieldIndex - 1 + fields.size()) % fields.size();
                errorMessage = null;
            }
            return true;
        }

        // Submit form
        if (type == KeyType.Enter) {
            if (onSubmit != null) {
                onSubmit.accept(this);
            }
            return true;
        }

        if (fields.isEmpty() || focusedFieldIndex >= fields.size()) {
            return true;
        }

        FormField field = fields.get(focusedFieldIndex);

        if (field.getType() == FieldType.OPTIONS) {
            if (type == KeyType.ArrowRight || (type == KeyType.Character && ch != null && ch == ' ')) {
                field.cycleOption(1);
                return true;
            } else if (type == KeyType.ArrowLeft) {
                field.cycleOption(-1);
                return true;
            }
        } else {
            // Text field editing
            if (type == KeyType.Character && ch != null && !Character.isISOControl(ch)) {
                field.insertChar(ch);
                return true;
            } else if (type == KeyType.Backspace) {
                field.backspace();
                return true;
            } else if (type == KeyType.Delete) {
                field.delete();
                return true;
            } else if (type == KeyType.ArrowLeft) {
                field.moveCursor(-1);
                return true;
            } else if (type == KeyType.ArrowRight) {
                field.moveCursor(1);
                return true;
            } else if (type == KeyType.Home) {
                field.cursorPosition = 0;
                return true;
            } else if (type == KeyType.End) {
                field.cursorPosition = field.textValue.length();
                return true;
            }
        }

        return true;
    }

    public void render(TextGraphics tg, int screenWidth, int screenHeight) {
        int width = Math.min(68, screenWidth - 4);
        int height = Math.min(fields.size() * 2 + 5, screenHeight - 2);

        int x = Math.max(2, (screenWidth - width) / 2);
        int y = Math.max(1, (screenHeight - height) / 2);

        // Fill background of modal
        tg.setBackgroundColor(TextColor.ANSI.BLACK);
        String blankRow = " ".repeat(width);
        for (int r = y; r < y + height; r++) {
            tg.putString(x, r, blankRow);
        }

        // Draw outer modal box
        TuiDrawingHelper.drawBox(tg, x, y, width, height, title, TextColor.ANSI.CYAN_BRIGHT, TextColor.ANSI.WHITE_BRIGHT);

        int rowY = y + 2;
        int maxInputWidth = width - 26;

        for (int i = 0; i < fields.size(); i++) {
            if (rowY >= y + height - 2) break;

            FormField field = fields.get(i);
            boolean isFocused = (i == focusedFieldIndex);

            String cursorMarker = isFocused ? "► " : "  ";
            String labelStr = String.format("%s%-14s ", cursorMarker, TuiDrawingHelper.truncate(field.getLabel(), 14));

            if (isFocused) {
                tg.setForegroundColor(TextColor.ANSI.CYAN_BRIGHT);
                tg.enableModifiers(SGR.BOLD);
                tg.putString(x + 2, rowY, labelStr);
                tg.clearModifiers();
            } else {
                tg.setForegroundColor(TextColor.ANSI.WHITE);
                tg.putString(x + 2, rowY, labelStr);
            }

            int inputX = x + 2 + labelStr.length();

            if (field.getType() == FieldType.OPTIONS) {
                String optText = "◄ " + field.getValue() + " ►";
                if (isFocused) {
                    tg.setBackgroundColor(TextColor.ANSI.BLUE);
                    tg.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
                    tg.enableModifiers(SGR.BOLD);
                    tg.putString(inputX, rowY, " " + optText + " ");
                    tg.clearModifiers();
                    tg.setBackgroundColor(TextColor.ANSI.BLACK);
                } else {
                    tg.setForegroundColor(TextColor.ANSI.WHITE);
                    tg.putString(inputX, rowY, optText);
                }
            } else {
                String val = field.getValue();
                String displayVal = TuiDrawingHelper.padRight(val, maxInputWidth);

                if (isFocused) {
                    tg.setBackgroundColor(TextColor.ANSI.BLUE);
                    tg.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
                    tg.putString(inputX, rowY, displayVal);

                    // Draw blinking/highlighted cursor
                    int curX = inputX + Math.min(field.cursorPosition, maxInputWidth - 1);
                    char curChar = field.cursorPosition < val.length() ? val.charAt(field.cursorPosition) : ' ';
                    tg.setBackgroundColor(TextColor.ANSI.WHITE);
                    tg.setForegroundColor(TextColor.ANSI.BLACK);
                    tg.enableModifiers(SGR.BOLD);
                    tg.putString(curX, rowY, String.valueOf(curChar));
                    tg.clearModifiers();

                    tg.setBackgroundColor(TextColor.ANSI.BLACK);
                } else {
                    tg.setForegroundColor(TextColor.ANSI.WHITE);
                    tg.putString(inputX, rowY, "[" + TuiDrawingHelper.truncate(val, maxInputWidth - 2) + "]");
                }
            }

            rowY += 2;
        }

        // Bottom row of modal (error message or instructions)
        int footerY = y + height - 2;
        if (errorMessage != null && !errorMessage.isBlank()) {
            tg.setForegroundColor(TextColor.ANSI.RED_BRIGHT);
            tg.enableModifiers(SGR.BOLD);
            tg.putString(x + 2, footerY, TuiDrawingHelper.truncate("❌ " + errorMessage, width - 4));
            tg.clearModifiers();
        } else {
            tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
            String hint = "[Tab / ▲ / ▼] Next Field  │  [Enter] Submit  │  [ESC] Cancel";
            tg.putString(x + 2, footerY, TuiDrawingHelper.truncate(hint, width - 4));
        }

        tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
    }
}
