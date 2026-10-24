package com.pruthviraj.inventory.ui;

import com.pruthviraj.inventory.config.Database;
import com.pruthviraj.inventory.dao.DemoDao;
import com.pruthviraj.inventory.demo.DemoPlan;
import java.util.Set;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Predicate;

/** Reads demo records through real application panels; never creates an order or sale. */
public final class DemoVisibilityChecks {
    private static int count;
    private static <T> T edt(Callable<T> action) throws Exception {var task=new FutureTask<T>(action);SwingUtilities.invokeAndWait(task);return task.get();}
    private static List<Component> components(Component root) {var list=new ArrayList<Component>();list.add(root);if(root instanceof Container c)for(var child:c.getComponents())list.addAll(components(child));return list;}
    private static <T extends Component> List<T> all(Component root,Class<T> type) {return components(root).stream().filter(type::isInstance).map(type::cast).toList();}
    private static void waitFor(Callable<Boolean> condition,String label) throws Exception {
        long until=System.nanoTime()+15_000_000_000L;while(System.nanoTime()<until){if(condition.call())return;Thread.sleep(75);}throw new AssertionError("Timeout: "+label);
    }
    private static void screenshot(InventoryFrame frame,String file) throws Exception {
        var image=edt(()->{var img=new BufferedImage(frame.getWidth(),frame.getHeight(),BufferedImage.TYPE_INT_RGB);var g=img.createGraphics();frame.paintAll(g);g.dispose();return img;});ImageIO.write(image,"png",Path.of("target",file).toFile());
    }
    private static long sampleRows(JTable table,Set<Integer> ids) {long count=0;for(int row=0;row<table.getRowCount();row++)if(ids.contains(((Number)table.getValueAt(row,0)).intValue()))count++;return count;}
    private static void formattedDates(Component root) {
        for(var table:all(root,JTable.class))for(int row=0;row<table.getRowCount();row++)for(int col=0;col<table.getColumnCount();col++) {
            Object raw=table.getValueAt(row,col);if(!(raw instanceof java.time.LocalDateTime))continue;
            JLabel rendered=(JLabel)table.prepareRenderer(table.getCellRenderer(row,col),row,col);
            if(!rendered.getText().equals(DateTimes.display(raw)))throw new AssertionError("Unexpected date/time display");
        }
    }
    private static void check(boolean condition,String label) {if(!condition)throw new AssertionError(label);count++;System.out.println("PASS visibility "+label);}
    public static void main(String[] args) throws Exception {
        Database db=new Database();DemoDao.Ids ids;
        try(var c=db.open()){var dao=new DemoDao();ids=dao.find(c,new DemoPlan());if(ids==null)throw new AssertionError("Samples missing");dao.audit(c);}
        var groups=List.of(Set.copyOf(ids.products().values()),Set.copyOf(ids.suppliers().values()),Set.copyOf(ids.customers().values()),Set.copyOf(ids.orders().values()));
        InventoryFrame frame=edt(()->{var f=new InventoryFrame(db);f.setVisible(true);return f;});
        try {
            JTabbedPane tabs=edt(()->all(frame,JTabbedPane.class).getFirst());
            for(int index=0;index<4;index++) {
                final int tab=index;Component panel=edt(()->{tabs.setSelectedIndex(tab);return tabs.getComponentAt(tab);});int expected=new int[]{10,4,6,8}[index];
                waitFor(()->edt(()->panel.isEnabled() && sampleRows(all(panel,JTable.class).getFirst(),groups.get(tab))==expected),"demo tab "+index);
                check(true,new String[]{"10 products including low/sold-out stock","4 suppliers","6 customers","8 purchase orders"}[index]);
                edt(()->{formattedDates(panel);return null;});
                if(index==0)screenshot(frame,"demo-products.png");
            }
            Component selling=edt(()->{tabs.setSelectedIndex(4);return tabs.getComponentAt(4);});
            waitFor(()->edt(()->selling.isEnabled() && all(selling,JComboBox.class).getFirst().getItemCount()>=6),"sale choices");
            check(edt(()->all(selling,JComboBox.class).stream().allMatch(c->c.getItemCount()>0)),"demo choices available in Sales");
            Component history=edt(()->{tabs.setSelectedIndex(5);return tabs.getComponentAt(5);});
            waitFor(()->edt(history::isEnabled),"history loaded");
            edt(()->{var fields=all(history,JTextField.class);fields.get(0).setText("01/10/2026 00:00");fields.get(1).setText("31/10/2026 23:59");all(history,JButton.class).stream().filter(b->b.getText().equals("Filter / Refresh")).findFirst().orElseThrow().doClick();return null;});
            waitFor(()->edt(()->history.isEnabled() && sampleRows(all(history,JTable.class).getFirst(),Set.copyOf(ids.sales().values()))==5),"sale history");
            edt(()->{formattedDates(history);return null;});check(true,"5 sales visible with minute-only dates in October history");screenshot(frame,"demo-history.png");
            System.out.println("Demo visibility checks passed: "+count);
        }finally{edt(()->{frame.dispose();return null;});}
    }
}
