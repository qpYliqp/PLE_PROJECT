import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

public class GamesCleaning extends Configured implements Tool {

    // Mapper class to read the input and process the JSON data
    public static class GamesCleaningMapper extends Mapper<LongWritable, Text, Text, IntWritable> {
        
        private static final ObjectMapper objectMapper = new ObjectMapper();
        
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            // Parse the JSON input
            String jsonString = value.toString();
            if (validJson(jsonString)) {
                // Validate the decks to have exactly 8 cards
                if (checkDeckSize(jsonString)) {
                    context.write(new Text("Valid Game"), new IntWritable(1));
                } else {
                    context.write(new Text("Invalid Deck Size"), new IntWritable(1));
                }
            } else {
                context.write(new Text("Invalid JSON"), new IntWritable(1));
            }
        }

        // Validate if the JSON string is correctly formatted
        private boolean validJson(String jsonString) {
            try {
                objectMapper.readTree(jsonString); // Try to parse the JSON
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        // Check that both players' decks have exactly 8 cards
        private boolean checkDeckSize(String jsonString) throws IOException {
            // Deserialize the JSON string into a Java object
            JsonNode rootNode = objectMapper.readTree(jsonString);
            JsonNode players = rootNode.path("players");

            for (JsonNode player : players) {
                String deck = player.path("deck").asText();
                if (deck.length() != 16) {  // Each deck should have 8 cards (2 characters per card)
                    return false;
                }
            }
            return true;
        }
    }

    // Reducer class to output the results after processing
    public static class GamesCleaningReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
        public void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
            int sum = 0;
            for (IntWritable val : values) {
                sum += val.get();
            }
            context.write(key, new IntWritable(sum));
        }
    }

    @Override
    public int run(String[] args) throws Exception {
        Configuration conf = getConf();
        Job job = Job.getInstance(conf, "Clean Population");
        job.setJarByClass(GamesCleaning.class);

        // Set input and output formats
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);
        job.setOutputFormatClass(TextOutputFormat.class);
        job.setInputFormatClass(TextInputFormat.class);

        // Set input and output paths
        try {
            FileInputFormat.addInputPath(job, new Path(args[0]));
            FileOutputFormat.setOutputPath(job, new Path(args[1]));
        } catch (Exception e) {
            System.out.println("Bad arguments, waiting for 2 arguments [inputURI] [outputURI]");
            return -1;
        }

        // Set mapper and reducer classes
        job.setMapperClass(GamesCleaningMapper.class);
        job.setReducerClass(GamesCleaningReducer.class);

        return job.waitForCompletion(true) ? 0 : 1;
    }

    public static void main(String[] args) throws Exception {
        System.exit(ToolRunner.run(new GamesCleaning(), args));
    }
}
