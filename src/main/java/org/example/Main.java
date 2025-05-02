package org.example;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

public class Main {

    private static final String SEARCH_API_URL = "https://openlibrary.org/search.json?title=%s";
    private static final String COVER_IMAGE_TEMPLATE = "https://covers.openlibrary.org/b/id/%s-M.jpg";
    private static final String DEFAULT_BOOKS_URL = "https://openlibrary.org/search.json?q=the&limit=100";

    private static final int MAX_RESULTS = 10;

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter a keyword to search books (or press enter to see all): ");
        String query = scanner.nextLine();
        scanner.close();

        List<BookDetails> books = fetchBooks(query);  // <-- Fix is here

        if (books.isEmpty()) {
            System.out.println("Sorry! No books found for this keyword.");
        } else {
            JSONArray jsonOutput = new JSONArray();
            for (BookDetails book : books) {
                jsonOutput.put(book.toJSON());
            }
            System.out.println(jsonOutput.toString(4));
            System.out.println("--------------------------------------------------------");
        }
    }
    public static List<BookDetails> fetchBooks(String query) throws IOException {
        List<BookDetails> bookList = new ArrayList<>();
        String url;

        if (query == null || query.trim().isEmpty()) {
            url = DEFAULT_BOOKS_URL;
        } else {
            String encodedQuery = URLEncoder.encode(query, "UTF-8");
            url = String.format(SEARCH_API_URL, encodedQuery);
        }

        String response = fetchData(url);
        JSONArray docs = new JSONObject(response).optJSONArray("docs");
        if (docs == null) return bookList;

        for (int i = 0; i < Math.min(docs.length(), MAX_RESULTS); i++) {
            JSONObject doc = docs.getJSONObject(i);
            JSONArray isbnArray = doc.optJSONArray("isbn");

            BookDetails book = null;
            if (isbnArray != null && isbnArray.length() > 0) {
                String isbn = isbnArray.getString(0); // use first ISBN
                book = fetchBookByISBN(isbn);
                if (book != null) {
                    bookList.add(book);
                    continue;
                }
            }

            book = buildFromSearchApi(doc);
            bookList.add(book);
        }

        return bookList;
    }


    private static BookDetails fetchBookByISBN(String isbn) {
        try {
            String booksApiUrl = "https://openlibrary.org/api/books?bibkeys=ISBN:" + isbn + "&format=json&jscmd=data";
            String jsonResponse = fetchData(booksApiUrl);

            JSONObject root = new JSONObject(jsonResponse);
            JSONObject bookData = root.optJSONObject("ISBN:" + isbn);

            if (bookData == null) return null;

            BookDetails book = new BookDetails();
            book.title = bookData.optString("title", "N/A");
            book.subtitle = bookData.optString("subtitle", "N/A");

            JSONArray authorsArray = bookData.optJSONArray("authors");
            List<String> authors = new ArrayList<>();
            if (authorsArray != null) {
                for (int i = 0; i < authorsArray.length(); i++) {
                    authors.add(authorsArray.getJSONObject(i).optString("name", "N/A"));
                }
            }
            book.authors = authors.toArray(new String[0]);

            JSONArray publishersArray = bookData.optJSONArray("publishers");
            List<String> publishers = new ArrayList<>();
            if (publishersArray != null) {
                for (int i = 0; i < publishersArray.length(); i++) {
                    publishers.add(publishersArray.getJSONObject(i).optString("name", "N/A"));
                }
            }
            book.publishers = publishers.toArray(new String[0]);

            book.publishYear = bookData.optInt("publish_date", -1); // Sometimes it's a string
            book.publishPlace = "N/A"; // Not provided in this API
            book.isbn = new String[] { isbn };

            if (bookData.has("cover")) {
                JSONObject cover = bookData.getJSONObject("cover");
                book.coverImage = cover.optString("medium", "N/A");
            } else {
                book.coverImage = "N/A";
            }

            book.description = bookData.has("description")
                    ? (bookData.get("description") instanceof JSONObject
                    ? bookData.getJSONObject("description").optString("value", "N/A")
                    : bookData.optString("description", "N/A"))
                    : "N/A";

            book.edition = "N/A";
            book.downloadUrl = "https://openlibrary.org" + bookData.optString("url", "N/A");

            return book;

        } catch (Exception e) {
            // Fail silently and fallback to search API version
            return null;
        }
    }



    private static BookDetails buildFromSearchApi(JSONObject doc) {
        BookDetails book = new BookDetails();
        book.title = doc.optString("title", "N/A");
        book.subtitle = doc.optString("subtitle", "N/A");
        book.authors = toArray(doc.optJSONArray("author_name"));
        book.publishers = toArray(doc.optJSONArray("publisher"));
        book.publishYear = doc.optInt("first_publish_year", -1);
        book.publishPlace = "N/A"; // Not available here
        book.isbn = toArray(doc.optJSONArray("isbn"));
        book.edition = doc.optString("edition_name", "N/A");

        if (doc.has("cover_i")) {
            book.coverImage = String.format(COVER_IMAGE_TEMPLATE, doc.getInt("cover_i"));
        } else {
            book.coverImage = "N/A";
        }

        Object desc = doc.opt("description");
        book.description = (desc instanceof JSONObject)
                ? ((JSONObject) desc).optString("value", "N/A")
                : (desc != null ? desc.toString() : "N/A");

        book.downloadUrl = "N/A";
        return book;
    }

    private static String fetchData(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder result = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            result.append(line);
        }

        reader.close();
        conn.disconnect();
        return result.toString();
    }

    private static String[] toArray(JSONArray array) {
        if (array == null) return new String[0];
        String[] result = new String[array.length()];
        for (int i = 0; i < array.length(); i++) {
            result[i] = array.optString(i, "N/A");
        }
        return result;
    }

    static class BookDetails {
        String title;
        String subtitle;
        String[] authors;
        String[] publishers;
        int publishYear;
        String publishPlace;
        String[] isbn;
        String coverImage;
        String description;
        String edition;
        String downloadUrl;

        public JSONObject toJSON() {
            JSONObject json = new JSONObject();
            json.put("title", title);
            json.put("subtitle", subtitle);
            json.put("authors", Arrays.asList(authors));
            json.put("publishers", Arrays.asList(publishers));
            json.put("publishYear", publishYear);
            json.put("publishPlace", publishPlace);
            json.put("isbn", Arrays.asList(isbn));
            json.put("coverImage", coverImage != null ? coverImage : "N/A");
            json.put("description", description);
            json.put("edition", edition);
            json.put("downloadUrl", downloadUrl != null ? downloadUrl : "N/A");
            return json;
        }

        @Override
        public String toString() {
            return "Title: " + title +
                    "\nSubtitle: " + subtitle +
                    "\nAuthors: " + Arrays.toString(authors) +
                    "\nPublishers: " + Arrays.toString(publishers) +
                    "\nPublish Year: " + publishYear +
                    "\nPublish Place: " + publishPlace +
                    "\nISBN(s): " + Arrays.toString(isbn) +
                    "\nCover: " + (coverImage != null ? coverImage : "N/A") +
                    "\nDescription: " + description +
                    "\nEdition: " + edition +
                    "\nDownload Link: " + (downloadUrl != null ? downloadUrl : "N/A");
        }
    }
}
