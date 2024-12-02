package crtracker;
import java.io.IOException;
import java.time.Instant;

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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.*;
public class GamesCleaning extends Configured implements Tool {

    
   

	
    public static class GamesCleaningMapper extends Mapper<LongWritable, Text, Text, Text> {

        
        
        // Méthode pour convertir une date ISO 8601 en timestamp (millisecondes)
      
    
        private Map<String, Long> lastTimestampMap = new HashMap<>();

        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            Gson gson = new Gson();
            String jsonLine = value.toString();
            Battle battle;
            
            try {
                battle = gson.fromJson(jsonLine, Battle.class);
            } catch (Exception e) {
                return; // Si le JSON est invalide, on l'ignore
            }
        
            // Vérifie que la partie a exactement 2 joueurs
            if (battle.players.size() != 2) return;
        
            String player1 = battle.players.get(0).utag;
            String player2 = battle.players.get(1).utag;
        
            if (player1 == null || player2 == null) return;
        
            // Tri des joueurs pour garantir l'unicité
            String first = player1.compareTo(player2) <= 0 ? player1 : player2;
            String second = player1.compareTo(player2) > 0 ? player1 : player2;
        
            // Crée une clé unique en incluant la date
            String uniqueKey = first + "," + second + "," + battle.mode + "," + battle.game + "," + battle.type;
        
           
        
          
            context.write(new Text(uniqueKey), new Text(jsonLine));
        }
    }


   


    public static class GamesCleaningReducer extends Reducer<Text, Text, Text, Text> {
        private long convertDateToTimestamp(String date) {
            try {
                return java.time.Instant.parse(date).toEpochMilli();
            } catch (Exception e) {
                return -1;  // Si la date est invalide
            }
        }

        public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            // Créer une liste pour stocker les parties avec la même clé unique
            List<String> validBattles = new ArrayList<>();
            
            // Pour comparer les dates, on va d'abord convertir les dates en timestamps
            List<Long> timestamps = new ArrayList<>();
    
            // On parcourt toutes les parties pour la clé unique donnée
            for (Text value : values) {
                String jsonLine = value.toString();
                Gson gson = new Gson();
                Battle battle;
    
                try {
                    battle = gson.fromJson(jsonLine, Battle.class);
                } catch (Exception e) {
                    continue; // Si le JSON est invalide, on passe à la suivante
                }
    
                // Convertir la date de la partie en timestamp
                long timestamp = convertDateToTimestamp(battle.date);
                timestamps.add(timestamp);
                validBattles.add(jsonLine); // Ajouter la partie à la liste pour validation
            }
    
            // Comparaison des timestamps pour détecter les parties proches (moins de 10 secondes d'écart)
            List<String> validFinalBattles = new ArrayList<>(validBattles); // Nouvelle liste pour stocker les parties valides
    
            for (int i = 0; i < timestamps.size(); i++) {
                long timestamp1 = timestamps.get(i);
                for (int j = i + 1; j < timestamps.size(); j++) {
                    long timestamp2 = timestamps.get(j);
                    
                    // Si la différence est inférieure à 10 secondes (10 000 ms), on ignore ces deux parties
                    if (Math.abs(timestamp1 - timestamp2) <= 10000) {
                        validFinalBattles.remove(i); // Retirer la partie à l'index i
                        validFinalBattles.remove(j); // Retirer la partie à l'index j
                        break;
                    }
                }
            }
    
            // Si des parties valides restent, on les écrit dans le contexte
            for (String battle : validFinalBattles) {
                context.write(key, new Text(battle));
            }
        }
    }
    
        

    



    public int run(String args[]) throws IOException, ClassNotFoundException, InterruptedException {
        Configuration conf = getConf();
        Job job = Job.getInstance(conf, "Clean population");
        job.setJarByClass(GamesCleaning.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        job.setOutputFormatClass(TextOutputFormat.class);
        job.setInputFormatClass(TextInputFormat.class);
        try {
            FileInputFormat.addInputPath(job, new Path(args[0]));
            FileOutputFormat.setOutputPath(job, new Path(args[1]));
        } 
        catch (Exception e) {
            System.out.println(" bad arguments, waiting for 2 arguments [inputURI] [outputURI]");
            return -1;
        }
    
        job.setMapperClass(GamesCleaningMapper.class);
        job.setReducerClass(GamesCleaningReducer.class);
        return job.waitForCompletion(true) ? 0 : 1;
    }
    
    public static void main(String args[]) throws Exception {
        System.exit(ToolRunner.run(new GamesCleaning(), args));
    }
        
    }
