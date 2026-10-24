package com.pruthviraj.inventory.ui;

import com.pruthviraj.inventory.config.Database;
import com.pruthviraj.inventory.model.Models.*;
import com.pruthviraj.inventory.service.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;

/** Drives real, visible Swing forms on the EDT. Appends fixtures only in inventory_test_*.
 * No third-party GUI automation or test framework is needed. */
public final class GuiChecks {
    private static InventoryFrame frame;
    private static int checks;
    private static <T> T edt(Callable<T> work) throws Exception {
        var task=new java.util.concurrent.FutureTask<T>(work);SwingUtilities.invokeAndWait(task);return task.get();
    }
    private static void check(boolean condition,String label) {if(!condition) throw new AssertionError(label);checks++;System.out.println("PASS GUI "+label);}
    private static List<Component> components(Component root) {
        var result=new ArrayList<Component>();result.add(root);
        if(root instanceof Container c) for(var child:c.getComponents()) result.addAll(components(child));return result;
    }
    private static <T extends Component> List<T> all(Component root,Class<T> type) {return components(root).stream().filter(type::isInstance).map(type::cast).toList();}
    private static void waitFor(Callable<Boolean> condition,String label) throws Exception {
        long deadline=System.nanoTime()+20_000_000_000L;
        while(System.nanoTime()<deadline) {if(condition.call()) return;Thread.sleep(75);}
        throw new AssertionError("Timeout: "+label);
    }
    private static JDialog dialog(String title) throws Exception {
        final JDialog[] found={null};waitFor(()->edt(()->{
            for(Window w:Window.getWindows()) if(w instanceof JDialog d && d.isShowing() && d.getTitle().equals(title)) {found[0]=d;return true;}return false;
        }),"dialog "+title);return found[0];
    }
    private static void click(Component root,String text) throws Exception {
        JButton b=edt(()->all(root,JButton.class).stream().filter(x->text.equals(x.getText()) && x.isShowing() && x.isEnabled()).findFirst().orElseThrow());
        SwingUtilities.invokeLater(b::doClick);
    }
    private static void ready(Component root) throws Exception {waitFor(()->edt(()->root.isEnabled() && !Boolean.TRUE.equals(root instanceof JComponent c?c.getClientProperty("inventory.busy"):null)),"form enabled");}
    private static Component tab(int index) throws Exception {
        return edt(()->{JTabbedPane tabs=all(frame,JTabbedPane.class).getFirst();tabs.setSelectedIndex(index);return tabs.getComponentAt(index);});
    }
    @SuppressWarnings("unchecked") private static void choose(JComboBox<?> combo,int id) {
        for(int i=0;i<combo.getItemCount();i++) {
            Object value=combo.getItemAt(i);int key=value instanceof Supplier s?s.id():value instanceof Product p?p.id():((Customer)value).id();
            if(key==id) {combo.setSelectedIndex(i);return;}
        }throw new AssertionError("Missing choice #"+id);
    }
    private static void dismissMessage() throws Exception {
        JDialog d=dialog("Message");click(d,"OK");waitFor(()->edt(()->!d.isShowing()),"message closed");
    }
    private static void screenshot(String name) throws Exception {
        BufferedImage img=edt(()->{BufferedImage image=new BufferedImage(frame.getWidth(),frame.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();frame.paintAll(g);g.dispose();return image;});
        ImageIO.write(img,"png",Path.of("target",name).toFile());
    }
    public static void main(String[] args) throws Exception {
        Path testPath=Path.of(System.getProperty("inventory.test.config","config/test-database.properties"));Database db=new Database(testPath);
        try(var c=db.open()){if(!c.getCatalog().matches("inventory_test_[A-Za-z0-9_]+")) throw new IllegalArgumentException("Refusing GUI checks outside inventory_test_*.");}
        var catalog=new CatalogService(db);var purchases=new PurchaseService(db);var sales=new SaleService(db);
        String tag="GUI "+System.nanoTime();int supplier=catalog.supplier(0,tag+" supplier",2,new BigDecimal("100"));
        int item=catalog.product(0,tag+" product"),customer=catalog.customer(0,tag+" customer");catalog.associate(item,supplier);
        try {
            frame=edt(()->{var f=new InventoryFrame(db);f.setVisible(true);return f;});
            check(edt(()->all(frame,JTabbedPane.class).getFirst().getTabCount()==6),"six tabs visible");
            Component purchaseTab=tab(3);ready(purchaseTab);click(purchaseTab,"Create order");
            JDialog create=dialog("Create purchase order");
            edt(()->{choose(all(create,JComboBox.class).get(0),supplier);List<JTextField> fields=all(create,JTextField.class);fields.get(0).setText("1");fields.get(1).setText("100");return null;});
            click(create,"Add / merge");waitFor(()->edt(()->all(create,JTable.class).getFirst().getRowCount()==1),"purchase line added");click(create,"Add / merge");
            waitFor(()->edt(()->all(create,JTable.class).getFirst().getValueAt(0,2).equals(2)),"duplicates merged");
            check(true,"duplicate purchase selections merged in form");
            click(create,"Save pending order");dismissMessage();
            int order=purchases.list(LocalDateTime.now().minusDays(1),LocalDateTime.now().plusDays(1)).stream().filter(p->p.supplierId()==supplier).findFirst().orElseThrow().id();
            check(purchases.lines(order).getFirst().quantity()==2,"pending order persisted from GUI");
            ready(purchaseTab);edt(()->{JTable table=all(purchaseTab,JTable.class).getFirst();for(int i=0;i<table.getRowCount();i++)if(table.getValueAt(i,0).equals(order)){table.setRowSelectionInterval(i,i);return null;}throw new AssertionError("Order missing");});
            click(purchaseTab,"Receive entire order");JDialog receive=dialog("Receive order #"+order);
            edt(()->{all(receive,JTextField.class).getFirst().setText("110.00");return null;});click(receive,"Confirm full receipt");
            waitFor(()->catalog.products(tag).getFirst().stock()==2,"receipt stock");check(true,"receipt form updates stock");
            Component selling=tab(4);ready(selling);
            edt(()->{List<JComboBox> combos=all(selling,JComboBox.class);choose(combos.get(0),customer);choose(combos.get(1),item);List<JTextField> fields=all(selling,JTextField.class);fields.get(0).setText("2");fields.get(1).setText("20");return null;});
            click(selling,"Add / set quantity");
            waitFor(()->edt(()->all(selling,JLabel.class).stream().anyMatch(l->l.getText().equals("Cart total: ₹264.00"))),"sale preview");
            check(true,"quantity/markup preview shows ₹132 per unit and ₹264 total");
            ready(selling);edt(()->{all(selling,JTextField.class).get(1).setText("0");return null;});
            waitFor(()->edt(()->all(selling,JLabel.class).stream().anyMatch(l->l.getText().equals("Cart total: ₹220.00"))),"markup refresh");
            check(true,"editing markup automatically recalculates preview");
            ready(selling);edt(()->{all(selling,JTextField.class).get(1).setText("20");return null;});
            waitFor(()->edt(()->all(selling,JLabel.class).stream().anyMatch(l->l.getText().equals("Cart total: ₹264.00"))),"restored markup");
            ready(selling);edt(()->{all(selling,JTextField.class).get(0).setText("3");return null;});click(selling,"Add / set quantity");
            JDialog insufficient=dialog("Inventory — operation failed");click(insufficient,"OK");ready(selling);
            check(edt(()->all(selling,JTable.class).getFirst().getValueAt(0,2).equals(3)),"insufficient-stock preview retains cart entries");
            edt(()->{all(selling,JTextField.class).get(0).setText("2");return null;});click(selling,"Add / set quantity");
            waitFor(()->edt(()->all(selling,JLabel.class).stream().anyMatch(l->l.getText().equals("Cart total: ₹264.00"))),"corrected quantity refresh");
            screenshot("gui-sale-preview.png");
            ready(selling);click(selling,"Save sale");dismissMessage();
            check(catalog.products(tag).getFirst().stock()==0,"saved sale deducts received stock");
            var saved=sales.list(LocalDateTime.now().minusDays(1),LocalDateTime.now().plusDays(1)).stream().filter(s->s.customer().equals(tag+" customer")).findFirst().orElseThrow();
            check(saved.total().compareTo(new BigDecimal("264.00"))==0 && sales.lines(saved.id()).getFirst().quantity()==2,"sale header and detail persisted from GUI");
            check(edt(()->all(selling,JTable.class).getFirst().getRowCount()==0),"successful save clears cart");
            Component history=tab(5);ready(history);check(edt(()->all(history,JTable.class).getFirst().getRowCount()>0),"history loads persisted sales");
            screenshot("gui-history.png");edt(()->{frame.dispose();return null;});
            // Simulate an unavailable server by changing only this frame's temporary config URL.
            Properties props=new Properties();try(var input=Files.newInputStream(testPath)){props.load(input);}String workingUrl=props.getProperty("db.url");
            props.setProperty("db.url","jdbc:mysql://127.0.0.1:1/inventory_test_unavailable");Path temporary=Files.createTempFile("inventory-gui-unavailable", ".properties");
            try {
                try(var out=Files.newOutputStream(temporary)){props.store(out,"Temporary GUI test");}
                frame=edt(()->{var f=new InventoryFrame(new Database(temporary));f.setVisible(true);return f;});
                JDialog error=dialog("Inventory — operation failed");
                check(edt(()->all(error,JOptionPane.class).stream().anyMatch(p->String.valueOf(p.getMessage()).contains("Cannot connect to MySQL"))),"unavailable MySQL gives understandable dialog");
                click(error,"OK");Component products=tab(0);ready(products);
                props.setProperty("db.url",workingUrl);try(var out=Files.newOutputStream(temporary)){props.store(out,"Temporary recovery test");}
                click(products,"Search / Refresh");waitFor(()->edt(()->all(products,JTable.class).getFirst().getRowCount()>0),"connection recovery");
                check(true,"controls reenable and refresh succeeds after reconnect");
            } finally {Files.deleteIfExists(temporary);}
            System.out.println("GUI checks passed: "+checks);
        } finally {edt(()->{for(Window w:Window.getWindows())w.dispose();return null;});}
    }
}
