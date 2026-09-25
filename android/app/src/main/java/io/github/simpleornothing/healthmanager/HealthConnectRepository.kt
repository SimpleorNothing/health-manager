package io.github.simpleornothing.healthmanager
import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class HealthDay(val date:String,val glucoseMgDl:Double?,val weightKg:Double?,val bodyFatPct:Double?,val steps:Long,val exerciseMinutes:Long,val caloriesKcal:Double?)
data class HealthSnapshot(val glucoseMgDl:Double?,val weightKg:Double?,val bodyFatPct:Double?,val steps:Long,val exerciseMinutes:Long,val caloriesKcal:Double?)
class HealthConnectRepository(context:Context){
 val client=HealthConnectClient.getOrCreate(context)
 val permissions=setOf(
  HealthPermission.getReadPermission(BloodGlucoseRecord::class),HealthPermission.getReadPermission(WeightRecord::class),
  HealthPermission.getReadPermission(BodyFatRecord::class),HealthPermission.getReadPermission(StepsRecord::class),
  HealthPermission.getReadPermission(ExerciseSessionRecord::class),HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))
 suspend fun hasPermissions()=client.permissionController.getGrantedPermissions().containsAll(permissions)
 suspend fun today():HealthSnapshot{
  val end=Instant.now(); val start=LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant(); val range=TimeRangeFilter.between(start,end)
  val glucose=client.readRecords(ReadRecordsRequest(BloodGlucoseRecord::class,range)).records.maxByOrNull{it.time}
  val weight=client.readRecords(ReadRecordsRequest(WeightRecord::class,range)).records.maxByOrNull{it.time}
  val fat=client.readRecords(ReadRecordsRequest(BodyFatRecord::class,range)).records.maxByOrNull{it.time}
  val exercises=client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class,range)).records
  val agg=client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL,TotalCaloriesBurnedRecord.ENERGY_TOTAL),range))
  return HealthSnapshot(glucose?.level?.inMilligramsPerDeciliter,weight?.weight?.inKilograms,fat?.percentage?.value,
   agg[StepsRecord.COUNT_TOTAL]?:0L,exercises.sumOf{ChronoUnit.MINUTES.between(it.startTime,it.endTime)},agg[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories)
 }
 suspend fun history(days:Long=30):List<HealthDay>{
  val zone=ZoneId.systemDefault(); val today=LocalDate.now(); val out=mutableListOf<HealthDay>()
  for(i in (days-1) downTo 0){
   val day=today.minusDays(i); val start=day.atStartOfDay(zone).toInstant(); val end=day.plusDays(1).atStartOfDay(zone).toInstant(); val range=TimeRangeFilter.between(start,end)
   val glucose=client.readRecords(ReadRecordsRequest(BloodGlucoseRecord::class,range)).records.maxByOrNull{it.time}
   val weight=client.readRecords(ReadRecordsRequest(WeightRecord::class,range)).records.maxByOrNull{it.time}
   val fat=client.readRecords(ReadRecordsRequest(BodyFatRecord::class,range)).records.maxByOrNull{it.time}
   val exercises=client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class,range)).records
   val agg=client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL,TotalCaloriesBurnedRecord.ENERGY_TOTAL),range))
   out.add(HealthDay(day.toString(),glucose?.level?.inMilligramsPerDeciliter,weight?.weight?.inKilograms,fat?.percentage?.value,agg[StepsRecord.COUNT_TOTAL]?:0L,exercises.sumOf{ChronoUnit.MINUTES.between(it.startTime,it.endTime)},agg[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories))
  }
  return out
 }
}