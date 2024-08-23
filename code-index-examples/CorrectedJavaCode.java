import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class FilterFruits {
  public static void main(String[] args) {
    List<String> items = new ArrayList<>(Arrays.asList("Apple", "Car", "Banana", "Table", "Orange", "Chair"));
    List<String> fruits = filterFruits(items);

    System.out.println("Fruits found:");
    for (String fruit : fruits) {
      System.out.println(fruit);
    }
  }

  // Method to filter fruits
  public static List<String> filterFruits(List<String> itemList) {
    List<String> fruits = new ArrayList<>();

    for (String item : itemList) {
      if (isFruit(item)) {
        fruits.add(item);
      }
    }
    return fruits;
  }

  // Method to check if an item is a fruit
  public static boolean isFruit(String item) {
    List<String> fruitList = Arrays.asList("Apple", "Banana", "Orange", "Pear", "Grape");

    // Case-insensitive comparison
    return fruitList.stream().anyMatch(fruit -> fruit.equalsIgnoreCase(item));
  }
}
