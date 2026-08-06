package net.readflow.model

/**
 * Опис одного тексту-зразка з реєстру `shared/samples/index.json`.
 *
 * Самі тексти лежать поруч із реєстром і на Android потрапляють в `assets/`
 * на етапі збірки (див. `SPEC_ANDROID.md`, розділ 5). У коді вони не дублюються:
 * додали файл у `shared/samples/` і рядок у реєстр — він з'явився в обох додатках.
 */
data class TextSample(

    /** Ідентифікатор із реєстру, напр. `sample-01`. */
    val id: String,

    /** Назва для списку вибору. */
    val title: String,

    /** Імʼя файлу поруч із реєстром, напр. `sample-01.txt`. */
    val file: String,

    /** Клас, для якого текст призначений (1–4). */
    val grade: Int,

    /** Рівень складності: «легкий», «середній», «складний». */
    val level: String,

    /** Кількість слів за правилами розділу 4 `SPEC.md`. */
    val words: Int
)
