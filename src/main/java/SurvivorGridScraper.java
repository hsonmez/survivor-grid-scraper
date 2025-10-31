import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class SurvivorGridScraper {

    public static void main(String[] args) {
        // Automatically downloads & sets up ChromeDriver
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");   // remove this if you want to see the browser
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-gpu");

        WebDriver driver = new ChromeDriver(options);

        try {
            String url = "https://www.survivorgrid.com/";
            driver.get(url);

            // Wait up to 15 seconds for the table to appear
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("table#grid")));

            // Parse HTML with Jsoup
            String html = driver.getPageSource();
            Document doc = Jsoup.parse(html);

            Element table = doc.selectFirst("table#grid");
            if (table == null) {
                System.err.println("Could not find table with id='grid'.");
                return;
            }

            // Extract headers
            Elements ths = table.select("thead th");
            if (ths.isEmpty()) {
                ths = table.select("tr").first().select("th");
            }
            List<String> headers = new ArrayList<>();
            for (Element th : ths) {
                headers.add(th.text().trim());
            }

            // Fallback: if no headers found
            if (headers.isEmpty()) {
                int colCount = table.select("tr").first().select("td").size();
                for (int i = 0; i < colCount; i++) {
                    headers.add("Column" + (i + 1));
                }
            }

            // Extract all data rows
            Elements rowElements = table.select("tbody tr");
            if (rowElements.isEmpty()) {
                rowElements = table.select("tr");
                if (!table.select("thead").isEmpty()
                        || !table.select("tr").first().select("th").isEmpty()) {
                    // skip header row if it had <th>
                    rowElements = rowElements.not(":first-of-type");
                }
            }

            List<List<String>> rows = new ArrayList<>();
            for (Element row : rowElements) {
                Elements cells = row.select("td, th");
                List<String> rowData = new ArrayList<>();
                for (Element cell : cells) {
                    rowData.add(cell.text().trim());
                }

                // Pad or trim to match header count
                while (rowData.size() < headers.size()) rowData.add("");
                if (rowData.size() > headers.size()) {
                    rowData = rowData.subList(0, headers.size());
                }
                rows.add(rowData);
            }

            // Write CSV
            String csvFile = "survivor_grid.csv";
            try (CSVPrinter printer = new CSVPrinter(
                    new FileWriter(csvFile),
                    CSVFormat.DEFAULT.withHeader(headers.toArray(new String[0]))
            )) {
                for (List<String> r : rows) {
                    printer.printRecord(r);
                }
            }
            System.out.println("✅ Wrote CSV to: " + new java.io.File(csvFile).getAbsolutePath());

            // Optional: also write XLSX
            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                XSSFSheet sheet = workbook.createSheet("Grid");
                int rnum = 0;
                Row headerRow = sheet.createRow(rnum++);
                for (int i = 0; i < headers.size(); i++) {
                    headerRow.createCell(i).setCellValue(headers.get(i));
                }
                for (List<String> r : rows) {
                    Row xr = sheet.createRow(rnum++);
                    for (int i = 0; i < r.size(); i++) {
                        xr.createCell(i).setCellValue(r.get(i));
                    }
                }
                try (FileOutputStream fos = new FileOutputStream("survivor_grid.xlsx")) {
                    workbook.write(fos);
                }
                System.out.println("✅ Wrote XLSX to: " + new java.io.File("survivor_grid.xlsx").getAbsolutePath());

            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
