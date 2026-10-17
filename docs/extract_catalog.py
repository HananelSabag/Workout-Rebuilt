"""Read-only extraction of the user's existing exercise CONTENT, not application code."""
import re, json
from pathlib import Path
from xml.etree import ElementTree as ET
old = Path(r'C:\CodingProjects\Workout-Tracker\app\src\main')
out = Path(r'C:\CodingProjects\Workout-Rebuilt\app\src\main\java\com\hananel\workout\data')
out.mkdir(parents=True, exist_ok=True)
def strings(folder):
    return {e.attrib['name']: ''.join(e.itertext()).replace("\\'", "'").replace('\\"', '"') for e in ET.parse(old/'res'/folder/'strings.xml').getroot() if e.tag == 'string'}
he, en = strings('values'), strings('values-en')
source = (old/'java/com/hananel/workouttracker2/data/ExerciseBank.kt').read_text(encoding='utf-8')
equipment = {'Dumbbells':'משקולות יד','Barbell':'מוט','Cable':'כבל','Machine':'מכונה','Bodyweight':'משקל גוף','Box/Bench':'ספסל','Treadmill/Bike':'הליכון / אופניים','Treadmill':'הליכון','Bike':'אופניים','Rowing Machine':'מכשיר חתירה','Elliptical':'אליפטיקל'}
lines=[]
q=lambda v: json.dumps(v, ensure_ascii=False)
for block in re.split(r'ExerciseBankItem\(', source)[1:]:
    def field(key, default):
        m=re.search(r'\b'+key+r'\s*=\s*("[^"]*"|\d+|true|false)',block)
        return m.group(1).strip('"') if m else default
    id=field('id','')
    if not id: continue
    key=re.search(r'nameResId\s*=\s*R.string.(\w+)',block).group(1)
    group=id.split('_')[1].upper()
    reps=field('defaultReps','12')
    target=int(re.search(r'\d+',reps).group())
    numbers=re.findall(r'\d+',reps)
    upper=int(numbers[1]) if len(numbers)>1 and '-' in reps else target
    unit='MINUTES' if 'min' in reps else 'SECONDS' if ('sec' in reps or id.endswith('plank')) else 'REPS'
    eq=field('equipment','')
    values=f'{q(id)}, {q(id)}, {q(he[key])}, {q(en.get(key,he[key]))}, Muscle.{group}, {q(equipment.get(eq,eq))}, {q(eq)}, {field("defaultSets","3")}, {target}, Measure.{unit}, {field("defaultRestTime","60")}, {field("hasWeight","true")}'
    lines.append('        Exercise('+values+', targetMax = '+str(upper)+'),')
(out/'Catalog.kt').write_text('''package com.hananel.workout.data

import com.hananel.workout.domain.*
import java.util.UUID

// Content adapted from the original Workout Tracker catalogue. No Android resource IDs are persisted.
object Catalog {
    val exercises = listOf(
'''+ '\n'.join(lines)+'''
    )
    fun fresh(exercise: Exercise) = exercise.copy(id = UUID.randomUUID().toString())
    fun starter(abc: Boolean = false, english: Boolean = false): List<Routine> {
        val groups = if (abc) listOf(
            Triple("A · חזה וכתפיים", "A · Push", listOf(Muscle.CHEST, Muscle.SHOULDERS, Muscle.ARMS)),
            Triple("B · גב וידיים", "B · Pull", listOf(Muscle.BACK, Muscle.ARMS)),
            Triple("C · רגליים ובטן", "C · Legs & core", listOf(Muscle.LEGS, Muscle.CORE))
        ) else listOf(
            Triple("חזה וכתפיים", "Chest & shoulders", listOf(Muscle.CHEST, Muscle.SHOULDERS)),
            Triple("גב", "Back", listOf(Muscle.BACK)),
            Triple("רגליים", "Legs", listOf(Muscle.LEGS)),
            Triple("ידיים ובטן", "Arms & core", listOf(Muscle.ARMS, Muscle.CORE)),
            Triple("אירובי", "Cardio", listOf(Muscle.CARDIO))
        )
        val scheduled = listOf(0, 2, 4, 6, 1)
        return groups.mapIndexed { index, (he, en, muscles) ->
            val selected = muscles.flatMap { m ->
                val candidates = exercises.filter { it.muscle == m }
                if (abc && m == Muscle.ARMS && index == 0) candidates.filter { it.libraryId.contains("tricep") }.take(1)
                else if (abc && m == Muscle.ARMS) candidates.filter { it.libraryId.contains("curl") }.take(1)
                else candidates.take(if (m == Muscle.CARDIO) 1 else if (muscles.size == 1) 3 else 2)
            }
            Routine(UUID.randomUUID().toString(), he,
                selected.map { fresh(it).copy(sets = if (it.measure == Measure.MINUTES) 1 else 3) }, setOf(scheduled[index]), nameEn = en, customName = false)
        }
    }
}
''',encoding='utf-8')
print(f'Extracted {len(lines)} exercises with Hebrew and English names')
