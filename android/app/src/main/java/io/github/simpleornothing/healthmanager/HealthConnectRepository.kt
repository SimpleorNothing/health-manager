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
import kotlinx.coroutines.CancellationException

data class HealthDay(val date:String,val glucoseMgDl:Double?,val weightKg:Double?,val bodyFatPct:Double?,val leanBodyMassKg:Double?,val steps:Long,val exerciseMinutes:Long,val caloriesKcal:Double?)
data class HealthSnapshot(val glucoseMgDl:Double?,val weightKg:Double?,val bodyFatPct:Double?,val leanBodyMassKg:Double?,val steps:Long,val exerciseMinutes:Long,val caloriesKcal:Double?,val diagnostics:Map<String,Any?>)
class HealthConnectRepository(context:Context){
 val client=HealthConnectClient.getOrCreate(context)
 val permissions=setOf(
  HealthPermission.getReadPermission(BloodGlucoseRecord::class),HealthPermission.getReadPermission(WeightRecord::class),
  HealthPermission.getReadPermission(BodyFatRecord::class),HealthPermission.getReadPermission(LeanBodyMassRecord::class),HealthPermission.getReadPermission(StepsRecord::class),
  HealthPermission.getReadPermission(ExerciseSessionRecord::class),HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
  HealthPermission.getWritePermission(BloodGlucoseRecord::class),HealthPermission.getWritePermission(WeightRecord::class),
  HealthPermission.getWritePermission(NutritionRecord::class),HealthPermission.getWritePermission(ExerciseSessionRecord::class))
 suspend fun hasPermissions()=client.permissionController.getGrantedPermissions().containsAll(permissions)
 private suspend fun <T> readSafely(label:String, errors:MutableMap<String,String>, block:suspend ()->T):T? = try { block() } catch(e:CancellationException) { throw e } catch(e:Exception) { errors[label]=e.message?:e.javaClass.simpleName; null }
 suspend fun today():HealthSnapshot{
  val errors=mutableMapOf<String,String>()
  val end=Instant.now(); val start=LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant(); val range=TimeRangeFilter.between(start,end)
  val glucose=readSafely("혈당",errors){client.readRecords(ReadRecordsRequest(BloodGlucoseRecord::class,range,ascendingOrder=false,pageSize=1)).records.firstOrNull()}
  val latestBodyRange=TimeRangeFilter.between(end.minus(30,ChronoUnit.DAYS),end)
  val weight=readSafely("체중",errors){client.readRecords(ReadRecordsRequest(WeightRecord::class,latestBodyRange,ascendingOrder=false,pageSize=1)).records.firstOrNull()}
  val fat=readSafely("체지방",errors){client.readRecords(ReadRecordsRequest(BodyFatRecord::class,latestBodyRange,ascendingOrder=false,pageSize=1)).records.firstOrNull()}
  val lean=readSafely("제지방",errors){client.readRecords(ReadRecordsRequest(LeanBodyMassRecord::class,latestBodyRange,ascendingOrder=false,pageSize=1)).records.firstOrNull()}
  val exercises=readSafely("운동",errors){client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class,range)).records}?:emptyList()
  val agg=readSafely("걸음·소모열량",errors){client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL,TotalCaloriesBurnedRecord.ENERGY_TOTAL),range))}
  return HealthSnapshot(glucose?.level?.inMilligramsPerDeciliter,weight?.weight?.inKilograms,fat?.percentage?.value,lean?.mass?.inKilograms,
   agg?.get(StepsRecord.COUNT_TOTAL)?:0L,exercises.sumOf{ChronoUnit.MINUTES.between(it.startTime,it.endTime)},agg?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories,
   mapOf("errors" to errors,"lookbackDays" to 30,"weightTime" to weight?.time?.toString(),"weightOrigin" to weight?.metadata?.dataOrigin?.packageName,"bodyFatTime" to fat?.time?.toString()))
 }
 suspend fun writeManual(kind:String,id:String,date:String,value:Double?=null,name:String?=null,minutes:Long?=null,kcal:Double?=null,carbs:Double?=null,protein:Double?=null,fat:Double?=null,mealType:String?=null){
  val zone=ZoneId.systemDefault(); val localDate=LocalDate.parse(date); val base=localDate.atTime(12,0).atZone(zone); val instant=base.toInstant()
  val metadata=androidx.health.connect.client.records.metadata.Metadata.manualEntry()
  val record:Record=when(kind){
   "glucose"->BloodGlucoseRecord(time=instant,zoneOffset=base.offset,level=androidx.health.connect.client.units.BloodGlucose.milligramsPerDeciliter(requireNotNull(value)),specimenSource=BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD,relationToMeal=BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN,metadata=metadata)
   "weight"->WeightRecord(time=instant,zoneOffset=base.offset,weight=androidx.health.connect.client.units.Mass.kilograms(requireNotNull(value)),metadata=metadata)
   "meal"->{ val end=instant.plus(30,ChronoUnit.MINUTES); NutritionRecord(startTime=instant,startZoneOffset=base.offset,endTime=end,endZoneOffset=base.offset,name=name,energy=kcal?.let{androidx.health.connect.client.units.Energy.kilocalories(it)},totalCarbohydrate=carbs?.let{androidx.health.connect.client.units.Mass.grams(it)},protein=protein?.let{androidx.health.connect.client.units.Mass.grams(it)},totalFat=fat?.let{androidx.health.connect.client.units.Mass.grams(it)},metadata=metadata) }
   "exercise"->{ val end=instant.plus((minutes?:1).coerceAtLeast(1),ChronoUnit.MINUTES); ExerciseSessionRecord(startTime=instant,startZoneOffset=base.offset,endTime=end,endZoneOffset=base.offset,exerciseType=ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,title=name,metadata=metadata) }
   else->throw IllegalArgumentException("지원하지 않는 Health Connect 기록: $kind")
  }
  client.insertRecords(listOf(record))
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