package com.pruthviraj.inventory.ui;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Every database task runs off the EDT. A task temporarily disables its whole form. */
final class Tasks {
    private Tasks() { }
    static <T> void run(Component root,Callable<T> work,Consumer<T> success) { run(root,work,success,e->error(root,e)); }
    static <T> void run(Component root,Callable<T> work,Consumer<T> success,Consumer<Throwable> failure) {
        if(!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Start UI tasks on the EDT.");
        if(root instanceof JComponent component && Boolean.TRUE.equals(component.getClientProperty("inventory.busy"))) return;
        if(root instanceof JComponent component) component.putClientProperty("inventory.busy",true);
        // Nested modal dialogs have their own root. Disabling the form also prevents double submission.
        Map<Component,Boolean> enabled=new IdentityHashMap<>(); disable(root,enabled);
        new SwingWorker<T,Void>() {
            protected T doInBackground() throws Exception { return work.call(); }
            protected void done() {
                enabled.forEach(Component::setEnabled);
                if(root instanceof JComponent component) component.putClientProperty("inventory.busy",false);
                try { success.accept(get()); }
                catch(java.util.concurrent.ExecutionException e) { failure.accept(e.getCause()); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); failure.accept(e); }
                catch(RuntimeException e) { failure.accept(e); }
            }
        }.execute();
    }
    private static void disable(Component c,Map<Component,Boolean> enabled) {
        enabled.put(c,c.isEnabled()); c.setEnabled(false);
        if(c instanceof Container container) for(Component child:container.getComponents()) disable(child,enabled);
    }
    static void error(Component parent,Throwable e) {
        String message;
        if(e instanceof SQLException sql) {
            String state=sql.getSQLState();
            if(state!=null && state.startsWith("23")) message="This change conflicts with a related record, an existing association, or a database constraint. Referenced records cannot be deleted.";
            else if(state!=null && state.startsWith("08")) message="Cannot connect to MySQL. Check that MySQL is running and config/database.properties is correct, then retry.";
            else if(state!=null && state.startsWith("28")) message="MySQL rejected the account credentials. Check config/database.properties.";
            else if(state!=null && state.startsWith("40")) message="Another operation changed the same records. Nothing was saved; refresh and retry.";
            else message="The database operation failed and was rolled back. Check configuration and console diagnostics, then retry.";
            System.err.println("Database failure: "+e.getClass().getName()+" SQLState="+state+" code="+sql.getErrorCode());
        } else if(e instanceof IllegalArgumentException || e instanceof ArithmeticException) message=e.getMessage()==null?"Enter valid numeric values.":e.getMessage();
        else { message="The operation could not finish. Your unsaved entries are retained."; System.err.println("Failure type: "+e.getClass().getName()); }
        // Do not print driver messages, JDBC URLs or credentials; stack frames retain diagnostic locations.
        for(var frame:e.getStackTrace()) if(frame.getClassName().startsWith("com.pruthviraj.inventory")) System.err.println("  at "+frame);
        JOptionPane.showMessageDialog(parent,message,"Inventory — operation failed",JOptionPane.ERROR_MESSAGE);
    }
}
