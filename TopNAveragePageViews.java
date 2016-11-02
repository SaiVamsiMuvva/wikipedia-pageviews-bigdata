/*
 * Get the Top N articles based on Average Page Views per day
 */
package wiki.wiki;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class TopNAveragePageViews {

    public static class TopNAveragePageViewsMap extends Mapper<LongWritable, Text, Text, LongWritable> {

        	@Override
    		public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
    			String delims = ",";
    			String[] wikiData = StringUtils.split(value.toString(), delims);
    			Text article = new Text(wikiData[0]);
    			LongWritable views = new LongWritable(Long.parseLong(wikiData[2]));
    			context.write(article, views);
    		}

    		@Override
    		protected void setup(Context context) throws IOException, InterruptedException {
    		}
    
    }
    
    /**
     * The reducer retrieves every word and puts it into a Map: if the word already exists in the
     * map, increments its value, otherwise sets it to 1.
     */
    public static class TopNAveragePageViewsReduce extends Reducer<Text, LongWritable, Text, DoubleWritable> {

        private  Map<Text, DoubleWritable> countMap = new HashMap<>();

        @Override
        public void reduce(Text key, Iterable<LongWritable> values, Context context) throws IOException, InterruptedException {

        	Long views = (long) 0;
			Long count = (long) 0;
			for (LongWritable t : values) {
				views += t.get();
				count++;
			}
			Double average = (double) views/ (double)count; 

            // puts the number of occurrences of this word into the map.
            // We need to create another Text object because the Text instance
            // we receive is the same for all the words
            countMap.put(new Text(key), new DoubleWritable(average));

        }

       @Override
        protected void cleanup(Context context) throws IOException, InterruptedException {
    
             Map<Text,DoubleWritable> sortedMap = sortByValues(countMap);
            int counter = 0;
            for (Text key : sortedMap.keySet()) {
                if (counter++ == 10) {
                    break;
                }
                
                context.write(key,sortedMap.get(key));
            }
        }
    }

    //This should not be used because, this is not associative and commutative
    public static class TopNAveragePageViewsCombiner extends Reducer<Text, LongWritable, Text, LongWritable> {

        @Override
        public void reduce(Text key, Iterable<LongWritable> values, Context context) throws IOException, InterruptedException {

        	Long views = (long) 0;
			for (LongWritable t : values) {
				views += t.get();
			}
            context.write(key, new LongWritable(views));
        }
    }

    /*
   * sorts the map by values. Taken from:
   * http://javarevisited.blogspot.it/2012/12/how-to-sort-hashmap-java-by-key-and-value.html
   */
    private static <K extends Comparable, V extends Comparable> Map<K, V> sortByValues(Map<K, V> map) {
        List<Map.Entry<K, V>> entries = new LinkedList<Map.Entry<K, V>>(map.entrySet());

        Collections.sort(entries, new Comparator<Map.Entry<K, V>>() {

            @Override
            public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });

        //LinkedHashMap will keep the keys in the order they are inserted
        //which is currently sorted on natural ordering
        Map<K, V> sortedMap = new LinkedHashMap<K, V>();

        for (Map.Entry<K, V> entry : entries) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        return sortedMap;
    }
    
    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf);
        job.setJobName("Top N");
        job.setJarByClass(TopNPageViews.class);
        job.setMapperClass(TopNAveragePageViewsMap.class);
        //job.setCombinerClass(TopNAveragePageViewsCombiner.class); //because average is not associative/commutative
        job.setReducerClass(TopNAveragePageViewsReduce.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(LongWritable.class);
       
        FileInputFormat.addInputPath(job, new Path("/Users/anushakaranam/Downloads/wiki/wikicounts"));
        FileOutputFormat.setOutputPath(job, new Path("TopNAveragePageViews"));
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }

}