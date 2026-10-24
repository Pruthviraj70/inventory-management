package com.pruthviraj.inventory;

import com.pruthviraj.inventory.config.Database;
import com.pruthviraj.inventory.ui.InventoryFrame;
import javax.swing.*;
import java.awt.GraphicsEnvironment;

public final class Main {
    private Main() { }
    public static void main(String[] args) {
        if(GraphicsEnvironment.isHeadless()) {System.err.println("A graphical desktop is required. Run mvn compile exec:java on your local desktop.");return;}
        SwingUtilities.invokeLater(()->new InventoryFrame(new Database()).setVisible(true));
    }
}
