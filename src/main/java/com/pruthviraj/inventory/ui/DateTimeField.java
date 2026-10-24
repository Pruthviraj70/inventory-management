package com.pruthviraj.inventory.ui;

import javax.swing.JTextField;
import java.time.LocalDateTime;

/** Minute input with an original full-precision value for lossless unchanged saves. */
public final class DateTimeField extends JTextField {
    private LocalDateTime original;
    public DateTimeField(LocalDateTime value) {super(17);setDateTime(value);}
    public void setDateTime(LocalDateTime value) {original=value;setText(DateTimes.format(value));}
    public LocalDateTime getDateTime() {return DateTimes.edited(getText(),original);}
}
