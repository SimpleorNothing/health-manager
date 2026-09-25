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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class HealthDay(val date:String,val glucoseMgDl:Double?,val weightKg:Double?,val bodyFatPct:Double?,val leanBodyMassKg:Double?,val steps:Long,val exerciseMinutes:Long,val caloriesKcal:Double?)
data class HealthSnapshot(val glucoseMgDl:Double?,val weightKg:Double?,val bodyFatPct:Double?,val leanBodyMassKg:Double?,val steps:Long,val exerciseMinutes:Long,val caloriesKcal:Double?)
class HealthConnectRepository(context:Context){
 val client=HealthConnectClient.getOrCreate(context)
 val permissions=setOf(
  HealthPermission.getReadPermission(BloodGlucoseRecord::class),HealthPermission.getReadPermission(WeightRecord::class),
  HealthPermission.getReadPermission(BodyFatRecord::class),HealthPermission.getReadPermission(LeanBodyMassRecord::class),HealthPermission.getReadPermission(StepsRecord::class),
  HealthPermission.getReadPermission(ExerciseSessionRecord::class),HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))
 suspend fun hasPermissions()=client.permissionController.getGrantedPermissions().containsAll(permissions)
 suspend fun today():HealthSnapshot{
  val end=Instant.now(); val start=LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant(); val range=TimeRangeFilter.between(start,end)
  val glucose=client.readRecords(ReadRecordsRequest(BloodGlucoseRecord::class,range)).records.maxByOrNull{it.time}
  val weight=client.readRecords(ReadRecordsRequest(WeightRecord::class,range)).records.maxByOrNull{it.time}
  val fat=client.readRecords(ReadRecordsRequest(BodyFatRecord::class,range)).records.maxByOrNull{it.time}
  val lean=client.readRecords(ReadRecordsRequest(LeanBodyMassRecord::class,range)).records.maxByOrNull{it.time}
  val exercises=client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class,range)).records
  val agg=client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL,TotalCaloriesBurnedRecord.ENERGY_TOTAL),range))
  return HealthSnapshot(glucose?.level?.inMilligramsPerDeciliter,weight?.weight?.inKilograms,fat?.percentage?.value,lean?.mass?.inKilograms,
   agg[StepsRecord.COUNT_TOTAL]?:0L,exercises.sumOf{ChronoUnit.MINUTES.between(it.startTime,it.endTime)},agg[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories)
 }
 suspend fun diagnostics():Map<String,Any?> {
  val end=Instant.now(); val start=end.minus(30,ChronoUnit.DAYS); val range=TimeRangeFilter.between(start,end)
  val granted=client.permissionController.getGrantedPermissions()
  fun yes(p:String)=granted.contains(p)
  val weight=client.readRecords(ReadRecordsRequest(WeightRecord::class,range)).records
  val fat=client.readRecords(ReadRecordsRequest(BodyFatRecord::class,range)).records
  val lean=client.readRecords(ReadRecordsRequest(LeanBodyMassRecord::class,range)).records
  return mapOf("weightCount" to weight.size,"bodyFatCount" to fat.size,"leanBodyMassCount" to lean.size,
   "weightPermission" to yes(HealthPermission.getReadPermission(WeightRecord::class)),
   "bodyFatPermission" to yes(HealthPermission.getReadPermission(BodyFatRecord::class)),
   "leanBodyMassPermission" to yes(HealthPermission.getReadPermission(LeanBodyMassRecord::class)))
 }
 suspend fun history(days:Long=30):List<HealthDay>{
  val zone=ZoneId.systemDefault(); val today=LocalDate.now(); val out=mutableListOf<HealthDay>()
  for(i in (days-1) downTo 0){
   val day=today.minusDays(i); val start=day.atStartOfDay(zone).toInstant(); val end=day.plusDays(1).atStartOfDay(zone).toInstant(); val range=TimeRangeFilter.between(start,end)
   val glucose=client.readRecords(ReadRecordsRequest(BloodGlucoseRecord::class,range)).records.maxByOrNull{it.time}
   val weight=client.readRecords(ReadRecordsRequest(WeightRecord::class,range)).records.maxByOrNull{it.time}
   val fat=client.readRecords(ReadRecordsRequest(BodyFatRecord::class,range)).records.maxByOrNull{it.time}
   val lean=client.readRecords(ReadRecordsRequest(LeanBodyMassRecord::class,range)).records.maxByOrNull{it.time}
   val exercises=client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class,range)).records
   val agg=client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL,TotalCaloriesBurnedRecord.ENERGY_TOTAL),range))
   out.add(HealthDay(day.toString(),glucose?.level?.inMilligramsPerDeciliter,weight?.weight?.inKilograms,fat?.percentage?.value,lean?.mass?.inKilograms,agg[StepsRecord.COUNT_TOTAL]?:0L,exercises.sumOf{ChronoUnit.MINUTES.between(it.startTime,it.endTime)},agg[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories))
  }
  return out
 }
}