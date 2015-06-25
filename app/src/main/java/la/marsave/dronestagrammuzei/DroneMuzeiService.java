package la.marsave.dronestagrammuzei;

import java.util.ArrayList;
import java.util.List;

import retrofit.http.GET;
import retrofit.http.Query;

/**
 * Created by sergiu on 24/06/15.
 */
public interface DroneMuzeiService {

    @GET("/?json=1&count=10")
    DataResponse getData(@Query("page") int page);

    class DataResponse {
        int pages;
        List<Post> posts;
    }

    class Post {

        private Integer id;

        private String type;
        
        private String slug;
        
        private String url;
        
        private String status;
        
        private String title;
        
        private String title_plain;
        
        private String content;
        
        private String excerpt;
        
        private String date;
        
        private String modified;
        
        private List<Category> categories = new ArrayList<>();
        
        private List<Tag> tags = new ArrayList<>();
        
        private Author author;
        
        private String thumbnail;

        private String thumbnail_size;

        private ThumbnailImages thumbnail_images;

        public Integer getId() {
            return id;
        }

        public String getUrl() {
            return url;
        }

        public ThumbnailImages getThumbnailImages() {
            return thumbnail_images;
        }

        public Author getAuthor() {
            return author;
        }

        public String getTitlePlain() {
            return title_plain;
        }
    }

    class ThumbnailImages {

        private Full full;

        private Thumbnail thumbnail;

        public Full getFull() {
            return full;
        }
    }

    class Author {

        private Integer id;

        private String slug;

        private String name;

        private String firstName;

        private String lastName;

        private String nickname;

        private String url;

        private String description;

        public String getName() {
            return name;
        }
    }

    class Category {

        private Integer id;

        private String slug;

        private String title;

        private String description;

        private Integer parent;

        private Integer postCount;
    }

    class Full {
        
        private String url;
        
        private Integer width;
        
        private Integer height;

        public String getUrl() {
            return url;
        }
    }

    class Thumbnail {

        private String url;

        private Integer width;

        private Integer height;
    }

    class Tag {

        private Integer id;
        
        private String slug;
        
        private String title;
        
        private String description;
        
        private Integer postCount;
    }
    
}