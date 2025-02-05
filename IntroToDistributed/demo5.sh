echo "maven test start!" | tee -a output.log
mvn test | tee -a output.log
echo "maven test end!" | tee -a output.log
echo "" | tee -a output.log
echo "deleting logs" | tee -a output.log
rm -rf logs* | tee -a output.log

echo "Starting Cluster" | tee -a output.log
java -cp target/classes/ edu.yu.cs.com3800.stage5.ServerMakerForDemo 0 8010 | tee -a output.log &
java -cp target/classes/ edu.yu.cs.com3800.stage5.ServerMakerForDemo 1 8020 | tee -a output.log &
java -cp target/classes/ edu.yu.cs.com3800.stage5.ServerMakerForDemo 2 8030 | tee -a output.log &
java -cp target/classes/ edu.yu.cs.com3800.stage5.ServerMakerForDemo 3 8040 | tee -a output.log &
java -cp target/classes/ edu.yu.cs.com3800.stage5.ServerMakerForDemo 4 8050 | tee -a output.log &
java -cp target/classes/ edu.yu.cs.com3800.stage5.ServerMakerForDemo 5 8060 | tee -a output.log &
java -cp target/classes/ edu.yu.cs.com3800.stage5.ServerMakerForDemo 6 8070 | tee -a output.log &
java -cp target/classes/ edu.yu.cs.com3800.stage5.ServerMakerForDemo 7 8080 | tee -a output.log &
java -cp target/classes/ edu.yu.cs.com3800.stage5.ServerMakerForDemo 8 8090 | tee -a output.log &

sleep 30
curl -s http://localhost:8888/getleader | tee -a output.log
echo "Starting Client" | tee -a output.log
java -cp target/classes/ edu.yu.cs.com3800.stage5.Client 8888 0 | tee -a output.log
sleep 30
echo "Killing worker 0" | tee -a output.log
pkill -9 -f "java -cp target/classes/ edu.yu.cs.com3800.stage5.ServerMakerForDemo 0 8010"
sleep 30
curl -s http://localhost:8888/getleader | tee -a output.log
echo "Killing leader 7" | tee -a output.log
pkill -9 -f "java -cp target/classes/ edu.yu.cs.com3800.stage5.ServerMakerForDemo 7 8080"
sleep 1
echo "Starting Client" | tee -a output.log
java -cp target/classes/ edu.yu.cs.com3800.stage5.Client 8888 1 | tee -a output.log
curl -s http://localhost:8888/getleader | tee -a output.log
echo "Starting Client for final single request" | tee -a output.log
java -cp target/classes/ edu.yu.cs.com3800.stage5.Client 8888 2 | tee -a output.log
log_dir=$(find . -type d -name "logs-Gossip")
if [ -d "$log_dir" ]; then
  for log_file in "$log_dir"/*.txt;
  do
    echo "$log_file" | tee -a output.log
  done
fi
pkill -f java




