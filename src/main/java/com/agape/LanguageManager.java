package com.agape;

public class LanguageManager {

    public static String getWelcomeMessage() {
        return "## 👋 Hello! Thanks for applying to the CCM matchmaking process!\n\n" +
                "**(1/15)** Let's start with your preferred language. What is your first language?\n" +
                "-# Please enter your preferred language below.\n" +
                "-# Por favor, especifique su idioma preferido.\n" +
                "-# Veuillez indiquer votre langue préférée.\n" +
                "-# Por favor, especifique o seu idioma de preferência.\n" +
                "-# Geef alstublieft uw voorkeurstaal op.\n";
    }

    public static boolean isSupportedLanguage(String language) {
        String[] supportedLanguages = {"english", "spanish", "french", "portuguese", "dutch", "german", "italian", "tagalog", "japanese", "chinese", "swahili", "afrikaans", "romanian"};
        for (String lang : supportedLanguages) {
            if (lang.equalsIgnoreCase(language)) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeLanguageName(String input) {
        if (input == null) return null;
        input = input.trim().toLowerCase();
        switch (input) {
            case "english": case "en": case "inglés": case "anglais": case "inglês": case "engels": case "englsih": case "inglsh": case "english (us)": case "english (uk)":
                return "english";
            case "spanish": case "español": case "es":
                return "spanish";
            case "french": case "français": case "fr":
                return "french";
            case "portuguese": case "português": case "pt":
                return "portuguese";
            case "dutch": case "nederlands": case "nl":
                return "dutch";
            case "german": case "deutsch": case "de":
                return "german";
            case "italian": case "italiano": case "it":
                return "italian";
            case "tagalog": case "filipino": case "tl":
                return "tagalog";
            case "japanese": case "nihongo": case "日本語": case "ja":
                return "japanese";
            case "chinese": case "mandarin": case "zhongwen": case "中文": case "zh":
                return "chinese";
            case "swahili": case "kiswahili": case "sw":
                return "swahili";
            case "afrikaans": case "af":
                return "afrikaans";
            case "romanian":
                return "romanian";
            default:
                return input;
        }
    }

    public static boolean isYes(String input) {
        input = input.trim().toLowerCase();
        return input.matches("^(yes|y|customize|c|sí|si|oui|sim|ja|oo|opo|sì|hai|はい|shi|是|ndiyo|da|personalizar|p|personalizza|anyo|カスタマイズ|自定义)$");
    }

    public static boolean isNo(String input) {
        input = input.trim().toLowerCase();
        return input.matches("^(no|n|submit|s|enviar|e|sumite|非|isubmit|提交|non|não|nao|nee|nein|hindi|iie|いいえ|bu|不|hapana|nu)$");
    }

    public static boolean isCancel(String input) {
        input = input.trim().toLowerCase();
        return input.matches("^(cancel|cancelar|annuler|annuleren|abbrechen|annulla|kanselahin|キャンセル|取消|ghairi|kanselleer|anulează)$");
    }

    public static String toTitleCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }

    public static String[] getQuestions(String language) {
        if (language == null) language = "english";
        switch (language.toLowerCase()) {
            case "spanish":
                return new String[] {
                    "¿Cómo prefieres que te llamen?\n-# Este es el nombre que aparecerá en tu tarjeta de perfil y que verán tus posibles parejas.",
                    "¿En qué país vives?",
                    "¿Cuántos años tienes?",
                    "¿Cuál es tu sexo?\n-# (Masculino / Femenino)",
                    "¿Cuál es tu denominación cristiana?\n-# ej., Bautista / Pentecostal / Sin denominación",
                    "Describe brevemente tu apariencia física.\n-# ej., \"1.70m y delgado\", \"1.90m 68 kg\"",
                    "¿Qué pasatiempos o intereses tienes que te gustaría compartir?\n-# ej., \"senderismo, cocinar, juegos de mesa\"",
                    "¿Cuáles son algunas de tus fortalezas?\n-# ej., \"sé escuchar, tengo un gran sentido del humor, de buen corazón\"",
                    "¿Cuáles son algunas de tus debilidades o áreas en las que estás trabajando para mejorar?\n-# ej., \"tímido/a al conocer gente, tiendo a pensar demasiado, a menudo soy impaciente\"",
                    "¡Por favor sube una foto tuya para usar en tu tarjeta de perfil!\n-# Si prefieres usar solo tu foto de perfil de Discord, escribe **skip**.",
                    "## ¡Genial! Ahora que te conozco mejor, vamos a ayudarte a encontrar una buena pareja.\n\n__PROGRESS_MAP__ ¿Qué rango de edad considerarías para posibles parejas?\n-# ej., 25-32, 18-24",
                    "¿Qué denominaciones cristianas considerarías para posibles parejas?\n-# ej., Bautista, Pentecostal, Sin denominación",
                    "¿Cuáles son tres cosas que buscas en una pareja?\n-# ej., \"amable, paciente, que sepa escuchar\"",
                    "¿Cuáles son tres 'líneas rojas' (red flags) que no tolerarías en una pareja?\n-# ej., \"fumar, decir groserías, manipulador/a\""
                };
            case "french":
                return new String[] {
                    "Comment préférez-vous qu'on vous appelle?\n-# C'est le nom qui apparaîtra sur votre carte de profil et que verront vos correspondances potentielles.",
                    "Dans quel pays vivez-vous?",
                    "Quel âge avez-vous?",
                    "Quel est votre sexe?\n-# (Masculin / Féminin)",
                    "Quelle est votre confession chrétienne?\n-# ex., Baptiste / Pentecôtiste / Non confessionnelle",
                    "Décrivez brièvement votre apparence physique.\n-# ex., \"1m70 et mince\", \"1m90 68 kg\"",
                    "Quels passe-temps ou intérêts aimeriez-vous partager?\n-# ex., \"randonnée, cuisine, jeux de société\"",
                    "Quelles sont quelques-unes de vos qualités?\n-# ex., \"à l'écoute, j'ai un bon sens de l'humour, grand cœur\"",
                    "Quels sont vos points faibles ou les domaines que vous cherchez à améliorer?\n-# ex., \"timide au premier abord, j'ai tendance à trop réfléchir, je suis souvent impatient(e)\"",
                    "Veuillez envoyer une photo de vous pour l'utiliser sur votre carte de profil!\n-# Si vous préférez utiliser uniquement votre photo de profil Discord, tapez **skip**.",
                    "## Super! Maintenant que je vous connais un peu mieux, aidons-vous à trouver un bon partenaire.\n\n__PROGRESS_MAP__ Quelle tranche d'âge considéreriez-vous pour un partenaire potentiel?\n-# ex., 25-32, 18-24",
                    "Quelles confessions chrétiennes considéreriez-vous pour des partenaires potentiels?\n-# ex., Baptiste, Pentecôtiste, Non confessionnelle",
                    "Quelles sont trois choses que vous recherchez chez un partenaire?\n-# ex., \"gentil, patient, à l'écoute\"",
                    "Quels sont trois critères rédhibitoires (red flags) pour vous chez un partenaire?\n-# ex., \"fumeur, grossier, manipulateur\""
                };
            case "portuguese":
                return new String[] {
                    "Como você prefere ser chamado(a)?\n-# Este é o nome que aparecerá no seu cartão de perfil e que os possíveis parceiros verão.",
                    "Em que país você mora?",
                    "Quantos anos você tem?",
                    "Qual é o seu sexo?\n-# (Masculino / Feminino)",
                    "Qual é a sua denominação cristã?\n-# ex., Batista / Pentecostal / Não denominacional",
                    "Descreva brevemente sua aparência física.\n-# ex., \"1,70m e magro\", \"1,90m 68 kg\"",
                    "Quais hobbies ou interesses você tem e gostaria de compartilhar?\n-# ex., \"fazer trilhas, cozinhar, jogar jogos de tabuleiro\"",
                    "Quais são algumas qualidades que você possui?\n-# ex., \"bom ouvinte, tenho um ótimo senso de humor, bondoso(a)\"",
                    "Quais são alguns pontos fracos ou áreas que você está tentando melhorar?\n-# ex., \"tímido(a) ao conhecer pessoas, costumo pensar demais, sou frequentemente impaciente\"",
                    "Por favor, envie uma foto sua para ser usada em seu cartão de perfil!\n-# Se preferir usar apenas a foto do seu perfil do Discord, digite **skip**.",
                    "## Ótimo! Agora que já conheço você melhor, vamos ajudar a encontrar um bom par.\n\n__PROGRESS_MAP__ Qual faixa etária você consideraria para possíveis parceiros?\n-# ex., 25-32, 18-24",
                    "Quais denominações cristãs você consideraria para possíveis parceiros?\n-# ex., Batista, Pentecostal, Não denominacional",
                    "Quais são três coisas que você procura em um parceiro?\n-# ex., \"gentil, paciente, bom ouvinte\"",
                    "Quais são três sinais de alerta (red flags) que você não tolera em um parceiro?\n-# ex., \"fumar, falar palavrões, manipulador(a)\""
                };
            case "dutch":
                return new String[] {
                    "Hoe word je het liefst genoemd?\n-# Dit is de naam die op je profielkaart verschijnt en die potentiële matches zullen zien.",
                    "In welk land woon je?",
                    "Hoe oud ben je?",
                    "Wat is je geslacht?\n-# (Man / Vrouw)",
                    "Wat is je christelijke stroming?\n-# bijv., Baptist / Pinkstergemeente / Niet-gebonden",
                    "Beschrijf kort je uiterlijk.\n-# bijv., \"1,70m en slank\", \"1,90m 68 kg\"",
                    "Welke hobby's of interesses heb je die je wilt delen?\n-# bijv., \"wandelen, koken, bordspellen spelen\"",
                    "Wat zijn een paar sterke punten van jezelf?\n-# bijv., \"kan goed luisteren, ik heb een goed gevoel voor humor, goedhartig\"",
                    "Wat zijn enkele zwakke punten of gebieden waaraan je werkt om te verbeteren?\n-# bijv., \"verlegen bij een eerste ontmoeting, ik denk vaak te veel na, ik ben vaak ongeduldig\"",
                    "Upload een foto van jezelf om te gebruiken op je profielkaart!\n-# Als je liever alleen je Discord-profielfoto gebruikt, typ dan **skip**.",
                    "## Geweldig! Nu ik je wat beter heb leren kennen, gaan we je helpen een goede match te vinden.\n\n__PROGRESS_MAP__ Welke leeftijdscategorie zou je overwegen voor potentiële matches?\n-# bijv., 25-32, 18-24",
                    "Welke christelijke stromingen zou je overwegen voor potentiële matches?\n-# bijv., Baptist, Pinkstergemeente, Niet-gebonden",
                    "Wat zijn drie dingen waar je naar op zoek bent in een partner?\n-# bijv., \"aardig, geduldig, kan goed luisteren\"",
                    "Wat zijn drie absolute afknappers (red flags) voor jou in een partner?\n-# bijv., \"roken, vloeken, manipulatief\""
                };
            case "german":
                return new String[] {
                    "Wie möchtest du genannt werden?\n-# Dies ist der Name, der auf deiner Profilkarte erscheint.",
                    "In welchem Land lebst du?",
                    "Wie alt bist du?",
                    "Was ist dein Geschlecht?\n-# (Männlich / Weiblich)",
                    "Was ist deine christliche Konfession?\n-# z.B. Baptist / Pfingstler / Konfessionslos",
                    "Beschreibe kurz dein Aussehen.\n-# z.B. \"1,70 m und schlank\", \"1,90 m, 75 kg\"",
                    "Welche Hobbys oder Interessen möchtest du teilen?\n-# z.B. \"Wandern, Kochen, Brettspiele\"",
                    "Was sind einige deiner Stärken?\n-# z.B. \"Guter Zuhörer, toller Humor, gutherzig\"",
                    "Was sind einige Schwächen, an denen du arbeitest?\n-# z.B. \"Schüchtern am Anfang, denke zu viel nach, oft ungeduldig\"",
                    "Bitte lade ein Bild von dir hoch!\n-# Tippe **skip**, wenn du nur dein Discord-Profilbild verwenden möchtest.",
                    "## Super! Jetzt, da ich dich besser kenne, lass uns einen guten Partner finden.\n\n__PROGRESS_MAP__ Welche Altersgruppe würdest du in Betracht ziehen?\n-# z.B. 25-32, 18-24",
                    "Welche christlichen Konfessionen würdest du in Betracht ziehen?\n-# z.B. Baptist, Pfingstler, Konfessionslos",
                    "Nenne drei Dinge, die du in einem Partner suchst.\n-# z.B. \"freundlich, geduldig, guter Zuhörer\"",
                    "Nenne drei absolute Ausschlusskriterien (Red Flags) bei einem Partner.\n-# z.B. \"Rauchen, Fluchen, manipulativ\""
                };
            case "italian":
                return new String[] {
                    "Come preferisci essere chiamato/a?\n-# Questo è il nome che apparirà sulla tua scheda profilo.",
                    "In quale paese vivi?",
                    "Quanti anni hai?",
                    "Qual è il tuo sesso?\n-# (Maschio / Femmina)",
                    "Qual è la tua confessione cristiana?\n-# es., Battista / Pentecostale / Aconfessionale",
                    "Descrivi brevemente il tuo aspetto fisico.\n-# es., \"1.70m e magro/a\", \"1.90m 75 kg\"",
                    "Quali hobby o interessi vorresti condividere?\n-# es., \"escursionismo, cucinare, giochi da tavolo\"",
                    "Quali sono alcuni dei tuoi punti di forza?\n-# es., \"so ascoltare, ho un grande senso dell'umorismo, di buon cuore\"",
                    "Quali sono alcuni punti deboli su cui stai lavorando?\n-# es., \"timido/a all'inizio, penso troppo, spesso impaziente\"",
                    "Carica una tua foto da usare sulla scheda del profilo!\n-# Se preferisci usare la tua immagine di Discord, digita **skip**.",
                    "## Ottimo! Ora che ti conosco meglio, troviamo il partner giusto.\n\n__PROGRESS_MAP__ Che fascia d'età considereresti per un partner?\n-# es., 25-32, 18-24",
                    "Quali confessioni cristiane considereresti per un partner?\n-# es., Battista, Pentecostale, Aconfessionale",
                    "Quali sono tre cose che cerchi in un partner?\n-# es., \"gentile, paziente, sa ascoltare\"",
                    "Quali sono tre segnali di allarme (red flags) inaccettabili in un partner?\n-# es., \"fuma, dice parolacce, manipolatore/trice\""
                };
            case "tagalog":
                return new String[] {
                    "Ano ang gusto mong itawag sa iyo?\n-# Ito ang pangalan na lalabas sa iyong profile card na makikita ng potential matches.",
                    "Saang bansa ka nakatira?",
                    "Ilang taon ka na?",
                    "Ano ang iyong kasarian?\n-# (Lalaki / Babae)",
                    "Ano ang iyong denominasyong Kristiyano?\n-# hal., Baptist / Pentecostal / Non-denominational",
                    "Ilarawan nang maikli ang iyong pisikal na anyo.\n-# hal., \"5'7 at payat\", \"6'3 150 lbs\"",
                    "Anong mga hobby o interes ang gusto mong ibahagi?\n-# hal., \"hiking, pagluluto, board games\"",
                    "Ano ang ilang mga kalakasan mo?\n-# hal., \"magaling makinig, may sense of humor, mabait\"",
                    "Ano ang ilang mga kahinaan o aspeto na pinapabuti mo pa?\n-# hal., \"mahiyain sa simula, overthinker, madaling mainip\"",
                    "Mangyaring mag-upload ng larawan mo para sa iyong profile card!\n-# I-type ang **skip** kung gusto mong gamitin ang iyong Discord profile picture.",
                    "## Ayos! Ngayong kilala na kita, humanap tayo ng magandang match para sa'yo.\n\n__PROGRESS_MAP__ Anong age range ang kinokonsidera mo para sa isang partner?\n-# hal., 25-32, 18-24",
                    "Anong mga denominasyong Kristiyano ang kinokonsidera mo para sa isang partner?\n-# hal., Baptist, Pentecostal, Non-denominational",
                    "Magbigay ng tatlong bagay na hinahanap mo sa isang partner.\n-# hal., \"mabait, pasensyoso, magaling makinig\"",
                    "Magbigay ng tatlong deal breakers (red flags) na ayaw mo sa isang partner.\n-# hal., \"naninigarilyo, nagmumura, mapanlinlang (manipulative)\""
                };
            case "japanese":
                return new String[] {
                    "どのようにお呼びすればよろしいですか？\n-# これはプロフィールカードに表示される名前です。",
                    "お住まいの国はどこですか？",
                    "年齢を教えてください。",
                    "性別を教えてください。\n-# (男性 / 女性)",
                    "あなたのキキリスト教の教派は何ですか？\n-# 例：バプテスト / ペンテコステ派 / 無教派",
                    "あなたの外見を簡単に説明してください。\n-# 例：「身長170cmで細身」、「身長180cmで体重70kg」",
                    "共有したい趣味や興味は何ですか？\n-# 例：「ハイキング、料理、ボードゲーム」",
                    "あなたの長所をいくつか教えてください。\n-# 例：「聞き上手、ユーモアのセンスがある、優しい」",
                    "改善しようとしている短所や弱点はありますか？\n-# 例：「初対面で人見知りする、考えすぎる、せっかち」",
                    "プロフィールカードに使用するあなたの写真をアップロードしてください！\n-# Discordのプロフィール画像を使用する場合は、**skip**と入力してください。",
                    "## 素晴らしい！あなたのことがよく分かりました。ぴったりのお相手を見つけましょう。\n\n__PROGRESS_MAP__ お相手の希望年齢層を教えてください。\n-# 例：25-32、18-24",
                    "お相手の希望する教派は何ですか？\n-# 例：バプテスト、ペンテコステ派、無教派",
                    "パートナーに求める3つの条件は何ですか？\n-# 例：「優しい、忍耐強い、聞き上手」",
                    "パートナーとして絶対に受け入れられない条件（レッドフラッグ）を3つ教えてください。\n-# 例：「喫煙、暴言、操作的」"
                };
            case "chinese":
                return new String[] {
                    "你希望大家怎么称呼你？\n-# 这是将显示在您的个人资料卡上的名字。",
                    "你居住在哪个国家？",
                    "你的年龄是多少？",
                    "你的性别是？\n-# (男 / 女)",
                    "你的基督教教派是什么？\n-# 例如：浸信会 / 五旬节派 / 非宗派",
                    "简要描述你的外貌。\n-# 例如：“170厘米，苗条”，“185厘米，70公斤”",
                    "你有哪些想分享的爱好或兴趣？\n-# 例如：“远足，烹饪，玩棋盘游戏”",
                    "你有哪些优点？\n-# 例如：“善于倾听，有幽默感，心地善良”",
                    "你有哪些缺点或正在努力改进的地方？\n-# 例如：“初次见面容易害羞，容易想太多，有时缺乏耐心”",
                    "请上传一张你的照片，用于你的个人资料卡！\n-# 如果你只想使用Discord头像，请回复 **skip**。",
                    "## 太棒了！现在我对你有了更多了解，让我们帮你寻找合适的伴侣吧。\n\n__PROGRESS_MAP__ 你希望伴侣的年龄范围是多少？\n-# 例如：25-32，18-24",
                    "你会考虑哪些基督教教派的伴侣？\n-# 例如：浸信会，五旬节派，非宗派",
                    "你在寻找伴侣时看重的三点是什么？\n-# 例如：“善良，有耐心，善于倾听”",
                    "伴侣身上的哪三个缺点是你绝对无法容忍的（红旗）？\n-# 例如：“抽烟，说脏话，喜欢操纵别人”"
                };
            case "swahili":
                return new String[] {
                    "Ungependa kuitwa nani?\n-# Hili ni jina litakaloonekana kwenye kadi yako ya wasifu.",
                    "Unaishi nchi gani?",
                    "Una umri wa miaka mingapi?",
                    "Jinsia yako ni nini?\n-# (Mwanamume / Mwanamke)",
                    "Dhehebu lako la Kikristo ni lipi?\n-# mf., Baptist / Pentekoste / Asiye na dhehebu",
                    "Eleza kwa ufupi mwonekano wako wa kimwili.\n-# mf., \"Mita 1.70 na mwembamba\", \"Mita 1.90 kilo 68\"",
                    "Una mapendeleo au maslahi gani ambayo ungependa kushiriki?\n-# mf., \"kutembea mlimani, kupika, michezo ya bodi\"",
                    "Je, ni baadhi ya nguvu zako zipi?\n-# mf., \"msikilizaji mzuri, mcheshi, mwenye moyo mzuri\"",
                    "Je, ni udhaifu gani unaofanyia kazi kuuboresha?\n-# mf., \"mwenye aibu mwanzoni, kufikiria sana, mara nyingi hukosa subira\"",
                    "Tafadhali pakia picha yako itakayotumika kwenye kadi yako ya wasifu!\n-# Ikiwa unapendelea kutumia picha yako ya Discord tu, andika **skip**.",
                    "## Vizuri! Sasa kwa kuwa nimekufahamu vizuri, hebu tukusaidie kupata mchumba mzuri.\n\n__PROGRESS_MAP__ Unapendelea mchumba wa umri gani?\n-# mf., 25-32, 18-24",
                    "Ungependa mchumba wa madhehebu gani ya Kikristo?\n-# mf., Baptist, Pentekoste, Asiye na dhehebu",
                    "Je, ni mambo gani matatu unayotafuta kwa mchumba?\n-# mf., \"mkarimu, mvumilivu, msikilizaji mzuri\"",
                    "Je, ni mambo gani matatu ambayo huwezi kuvumilia (red flags) kwa mchumba?\n-# mf., \"kuvuta sigara, kutukana, mjanja\""
                };
            case "afrikaans":
                return new String[] {
                    "Wat wil jy graag genoem word?\n-# Dit is die naam wat op jou profielkaart sal verskyn.",
                    "In watter land woon jy?",
                    "Hoe oud is jy?",
                    "Wat is jou geslag?\n-# (Man / Vrou)",
                    "Wat is jou Christelike denominasie?\n-# bv., Baptis / Pinkster / Nie-denominasioneel",
                    "Beskryf kortliks jou fisiese voorkoms.\n-# bv., \"1.70m en skraal\", \"1.90m 68 kg\"",
                    "Watter stokperdjies of belangstellings het jy wat jy wil deel?\n-# bv., \"stap, kook, bordspeletjies\"",
                    "Wat is 'n paar van jou sterkpunte?\n-# bv., \"goeie luisteraar, goeie sin vir humor, saggeaard\"",
                    "Wat is sommige swakpunte waaraan jy werk?\n-# bv., \"skaam as ek mense ontmoet, dink te veel, dikwels ongeduldig\"",
                    "Laai asseblief 'n foto van jouself op vir jou profielkaart!\n-# As jy net jou Discord-profielfoto wil gebruik, tik **skip**.",
                    "## Wonderlik! Noudat ek jou beter leer ken het, kom ons help om vir jou 'n goeie pasmaat te vind.\n\n__PROGRESS_MAP__ Watter ouderdomsgroep sal jy oorweeg vir moontlike pasmaats?\n-# bv., 25-32, 18-24",
                    "Watter Christelike denominasies sal jy oorweeg vir moontlike pasmaats?\n-# bv., Baptis, Pinkster, Nie-denominasioneel",
                    "Wat is drie dinge waarna jy soek in 'n lewensmaat?\n-# bv., \"vriendelik, geduldig, goeie luisteraar\"",
                    "Wat is drie rooi vlae wat onaanvaarbaar vir jou in 'n lewensmaat sal wees?\n-# bv., \"rook, vloek, manipulerend\""
                };
            case "romanian":
                return new String[] {
                    "Cum preferi să fii numit(ă)?\n-# Acesta este numele care va apărea pe cardul tău de profil.",
                    "În ce țară locuiești?",
                    "Câți ani ai?",
                    "Care este sexul tău?\n-# (Bărbat / Femeie)",
                    "Care este confesiunea ta creștină?\n-# ex., Baptist / Penticostal / Nedenominațional",
                    "Descrie pe scurt aspectul tău fizic.\n-# ex., \"1.70m și slab(ă)\", \"1.90m 75 kg\"",
                    "Ce hobby-uri sau interese ai și ai dori să le împărtășești?\n-# ex., \"drumeții, gătit, jocuri de societate\"",
                    "Care sunt câteva dintre punctele tale forte?\n-# ex., \"ascultător bun, am un simț al umorului excelent, suflet bun\"",
                    "Care sunt câteva dintre punctele tale slabe la care lucrezi?\n-# ex., \"timid(ă) la prima vedere, mă gândesc prea mult, adesea nerăbdător/oare\"",
                    "Te rugăm să încarci o poză cu tine pentru a fi folosită pe cardul de profil!\n-# Dacă preferi să folosești doar poza de profil de pe Discord, tastează **skip**.",
                    "## Super! Acum că te cunosc mai bine, hai să te ajutăm să-ți găsești o pereche potrivită.\n\n__PROGRESS_MAP__ Ce interval de vârstă ai lua în considerare pentru o potențială pereche?\n-# ex., 25-32, 18-24",
                    "Ce confesiuni creștine ai lua în considerare pentru o potențială pereche?\n-# ex., Baptist, Penticostal, Nedenominațional",
                    "Care sunt trei lucruri pe care le cauți la un partener?\n-# ex., \"bun, răbdător, ascultător bun\"",
                    "Care sunt trei semnale de alarmă (red flags) pe care nu le-ai accepta la un partener?\n-# ex., \"fumează, înjură, manipulator\""
                };
            default:
                return new String[] {
                    "What is your preferred name?\n-# This is the name that will appear on your profile card and that potential matches will see.",
                    "What country do you live in?",
                    "How old are you?",
                    "What is your sex?\n-# (Male / Female)",
                    "What is your Christian denomination?\n-# e.g., Baptist / Pentecostal / Non-denominational",
                    "Briefly describe your physical appearance.\n-# e.g., \"5'7\" and slim\", \"6'3 150 lbs\"",
                    "What hobbies or interests do you have that you'd like to share?\n-# e.g., \"hiking, cooking, playing board games\"",
                    "What are a few strengths you possess?\n-# e.g., \"good listener, I have a great sense of humor, kind-hearted\"",
                    "What are some weaknesses or areas you're working on improving?\n-# e.g., \"shy when first meeting, I tend to overthink things, I'm often impatient\"",
                    "Please upload a picture of yourself to be used on your profile card!\n-# If you prefer to just use your Discord Profile Picture, type **skip**.",
                    "## Great! Now that I've gotten to know you better, let's help you find a good match.\n\n__PROGRESS_MAP__ What age range would you consider for potential matches?\n-# e.g., 25-32, 18-24",
                    "What Christian denominations would you consider for potential matches?\n-# e.g., Baptist, Pentecostal, Non-denominational",
                    "What are three things you look for in a partner?\n-# e.g., \"kind, patient, good listener\"",
                    "What are three red flags you might see in a partner?\n-# e.g., \"smoking, swearing, manipulative\""
                };
        }
    }

    public static String getTargetAgeValidationError(String language) {
        if (language == null) return "Please enter a valid age range.\n-# Examples: 25, 22-28, 18 - 30";
        switch (language.toLowerCase()) {
            case "spanish": return "Por favor ingresa un rango de edad válido.\n-# Ejemplos: 25, 22-28, 18 - 30";
            case "french": return "Veuillez entrer une tranche d'âge valide.\n-# Exemples : 25, 22-28, 18 - 30";
            case "portuguese": return "Por favor, insira um intervalo de idade válido.\n-# Exemplos: 25, 22-28, 18 - 30";
            case "dutch": return "Voer alstublieft een geldig leeftijdsbereik in.\n-# Voorbeelden: 25, 22-28, 18 - 30";
            case "german": return "Bitte gib einen gültigen Altersbereich ein.\n-# Beispiele: 25, 22-28, 18 - 30";
            case "italian": return "Inserisci un intervallo di età valido.\n-# Esempi: 25, 22-28, 18 - 30";
            case "tagalog": return "Mangyaring magbigay ng valid na edad.\n-# Mga halimbawa: 25, 22-28, 18 - 30";
            case "japanese": return "有効な年齢範囲を入力してください。\n-# 例：25、22-28、18 - 30";
            case "chinese": return "请输入有效的年龄范围。\n-# 例子：25、22-28、18 - 30";
            case "swahili": return "Tafadhali ingiza eneo la umri halali.\n-# Mifano: 25, 22-28, 18 - 30";
            case "afrikaans": return "Voer asseblief 'n geldige ouderdomsreeks in.\n-# Voorbeelde: 25, 22-28, 18 - 30";
            case "romanian": return "Vă rugăm să introduceți un interval de vârstă valid.\n-# Exemple: 25, 22-28, 18 - 30";
            default: return "Please enter a valid age range.\n-# Examples: 25, 22-28, 18 - 30";
        }
    }

    public static String getGeneratingMessage(String language) {
        if (language == null) return "Generating your preview card...";
        switch (language.toLowerCase()) {
            case "spanish": return "Generando tu tarjeta de vista previa...";
            case "french": return "Génération de votre carte d'aperçu...";
            case "portuguese": return "Gerando seu cartão de pré-visualização...";
            case "dutch": return "Je voorbeeldkaart genereren...";
            case "german": return "Erstelle deine Vorschau-Karte...";
            case "italian": return "Generazione della tua scheda di anteprima...";
            case "tagalog": return "Binubuo ang iyong preview card...";
            case "japanese": return "プレビューカードを作成中...";
            case "chinese": return "正在生成您的预览卡...";
            case "swahili": return "Inatengeneza kadi yako ya hakikisho...";
            case "afrikaans": return "Genereer jou voorskoukaart...";
            case "romanian": return "Se generează cardul de previzualizare...";
            default: return "Generating your preview card...";
        }
    }

    public static String getYesNoWarning(String language) {
        if (language == null) return "Please answer with customize, edit, or submit.";
        switch (language.toLowerCase()) {
            case "spanish": return "Por favor responda con 'personalizar', 'edit', o 'enviar'.";
            case "french": return "Veuillez répondre par 'personnaliser', 'edit', ou 'envoyer'.";
            case "portuguese": return "Por favor responda com 'personalizar', 'edit', ou 'enviar'.";
            case "dutch": return "Beantwoord alstublieft met 'aanpassen', 'edit', of 'indienen'.";
            case "german": return "Bitte antworte mit 'anpassen', 'edit', oder 'absenden'.";
            case "italian": return "Rispondi con 'personalizza', 'edit', o 'invia'.";
            case "tagalog": return "Pakisagot ng 'i-customize', 'edit', o 'isumite'.";
            case "japanese": return "「カスタマイズ」、「edit」、または「送信」で答えてください。";
            case "chinese": return "请回答customize、edit或submit。";
            case "swahili": return "Tafadhali jibu 'badilisha', 'edit', au 'wasilisha'.";
            case "afrikaans": return "Antwoord asseblief met 'pas aan', 'edit', of 'dien in'.";
            case "romanian": return "Vă rugăm să răspundeți cu 'personalizare', 'edit', sau 'trimite'.";
            default: return "Please answer with customize, edit, or submit.";
        }
    }

    public static String getCustomizationTitle(String language) {
        if (language == null) return "🎨 What would you like to do?";
        switch (language.toLowerCase()) {
            case "spanish": return "🎨 ¿Qué te gustaría hacer?";
            case "french": return "🎨 Que souhaitez-vous faire?";
            case "portuguese": return "🎨 O que você gostaria de fazer?";
            case "dutch": return "🎨 Wat wil je graag doen?";
            case "german": return "🎨 Was möchtest du tun?";
            case "italian": return "🎨 Cosa vorresti fare?";
            case "tagalog": return "🎨 Ano ang gusto mo nang gawin?";
            case "japanese": return "🎨 何をしたいですか？";
            case "chinese": return "🎨 您想做什么？";
            case "swahili": return "🎨 Ungependa kufanya nini?";
            case "afrikaans": return "🎨 Wat wil jy graag doen?";
            case "romanian": return "🎨 Ce doriți să faceți?";
            default: return "🎨 What would you like to do?";
        }
    }

    public static String getCustomizationDescription(String language) {
        if (language == null) return "Reply with **customize**, **edit**, or **submit**, or use the buttons below:";
        switch (language.toLowerCase()) {
            case "spanish": return "Responde con **personalizar**, **edit**, o **enviar**, o usa los botones:";
            case "french": return "Répondez avec **personnaliser**, **edit**, ou **envoyer**, ou utilisez les boutons :";
            case "portuguese": return "Responda com **personalizar**, **edit**, ou **enviar**, ou use os botões:";
            case "dutch": return "Antwoord met **aanpassen**, **edit**, of **indienen**, of gebruik de knoppen:";
            case "german": return "Antworte mit **anpassen**, **edit**, oder **absenden**, oder benutze die Schaltflächen:";
            case "italian": return "Rispondi con **personalizza**, **edit**, o **invia**, o usa i pulsanti:";
            case "tagalog": return "Sumagot ng **i-customize**, **edit**, o **isumite**, o gamitin ang mga button:";
            case "japanese": return "**カスタマイズ**、**edit**、または**送信**で答えるか、ボタンを使用してください：";
            case "chinese": return "回复 **自定义**、**edit** 或 **提交**，或使用下面的按钮：";
            case "swahili": return "Jibu na **badilisha**, **edit**, au **wasilisha**, au tumia vitufe:";
            case "afrikaans": return "Antwoord met **pas aan**, **edit**, of **dien in**, of gebruik die knoppies:";
            case "romanian": return "Răspundeți cu **personalizare**, **edit**, sau **trimite**, sau utilizați butoanele:";
            default: return "Reply with **customize**, **edit**, or **submit**, or use the buttons below:";
        }
    }

    public static String getDesignCodePrompt(String language, String url) {
        if (language == null) return "Please visit " + url + " to create your custom design.\nOnce you're done, copy and paste the **Design Code** here!\n-# (If you changed your mind, type **cancel** to go back).";
        switch (language.toLowerCase()) {
            case "spanish": return "Visita " + url + " para crear tu diseño personalizado.\n¡Una vez que hayas terminado, copia y pega el **Código de Diseño** aquí!\n-# (Si cambiaste de opinión, escribe **cancel** para volver).";
            case "french": return "Visitez " + url + " pour créer votre design.\nUne fois terminé, copiez et collez le **Code de Design** ici!\n-# (Si vous changez d'avis, tapez **cancel** pour revenir).";
            case "portuguese": return "Visite " + url + " para criar seu design.\nQuando terminar, copie e cole o **Código de Design** aqui!\n-# (Se você mudou de ideia, digite **cancel** para voltar).";
            case "dutch": return "Bezoek " + url + " om je ontwerp te maken.\nZodra je klaar bent, kopieer en plak de **Design Code** hier!\n-# (Typ **cancel** om terug te gaan).";
            case "german": return "Besuche " + url + " für dein Design.\nKopiere dann den **Design Code** hierher!\n-# (Tippe **cancel**, um zurückzugehen).";
            case "italian": return "Visita " + url + " per il tuo design.\nIncolla il **Design Code** qui!\n-# (Digita **cancel** per tornare indietro).";
            case "tagalog": return "Bisitahin ang " + url + " para sa iyong disenyo.\nI-paste ang **Design Code** dito!\n-# (I-type ang **cancel** para bumalik).";
            case "japanese": return url + " にアクセスしてデザインを作成し、**デザインコード**をここに貼り付けてください！\n-# （戻る場合は **cancel** と入力）。";
            case "chinese": return "请访问 " + url + " 创建设计，并将 **设计代码** 粘贴到这里！\n-# （回复 **cancel** 返回）。";
            case "swahili": return "Tembelea " + url + " kufanya muundo wako.\nBandika **Design Code** hapa!\n-# (Andika **cancel** kurudi).";
            case "afrikaans": return "Besoek " + url + " vir jou ontwerp.\nPlak die **Design Code** hier!\n-# (Tik **cancel** om terug te gaan).";
            case "romanian": return "Vizitați " + url + " pentru designul dvs.\nLipiți **Design Code** aici!\n-# (Tastați **cancel** pentru a vă întoarce).";
            default: return "Please visit " + url + " to create your custom design.\nOnce you're done, copy and paste the **Design Code** here!\n-# (If you changed your mind, type **cancel** to go back).";
        }
    }

    public static String getCompletionMessage(String language) {
        if (language == null) return "Application Complete! Your profile is now being processed.";
        switch (language.toLowerCase()) {
            case "spanish": return "¡Solicitud Completa! Tu perfil está siendo procesado.";
            case "french": return "Candidature Terminée ! Votre profil est en cours de traitement.";
            case "portuguese": return "Inscrição Concluída! Seu perfil está sendo processado.";
            case "dutch": return "Sollicitatie Voltooid! Je profiel wordt nu verwerkt.";
            case "german": return "Bewerbung Abgeschlossen! Dein Profil wird nun verarbeitet.";
            case "italian": return "Candidatura Completata! Il tuo profilo è ora in fase di elaborazione.";
            case "tagalog": return "Kumpleto na ang Aplikasyon! Pinoproseso na ngayon ang iyong profile.";
            case "japanese": return "申し込み完了！あなたのプロフィールは現在処理中です。";
            case "chinese": return "申请完成！您的个人资料正在处理中。";
            case "swahili": return "Maombi Yamekamilika! Wasifu wako sasa unashughulikiwa.";
            case "afrikaans": return "Aansoek Voltooi! Jou profiel word nou verwerk.";
            case "romanian": return "Aplicație Completă! Profilul dvs. este acum în curs de procesare.";
            default: return "Application Complete! Your profile is now being processed.";
        }
    }

    public static String getInvalidAgeWarning(String language) {
        if (language == null) return "The age you entered is invalid. Please provide your real age.";
        switch (language.toLowerCase()) {
            case "spanish": return "La edad que ingresaste no es válida. Por favor, proporciona tu edad real.";
            case "french": return "L'âge que vous avez entré n'est pas valide. Veuillez fournir votre âge réel.";
            case "portuguese": return "A idade que você inseriu é inválida. Por favor, forneça sua idade real.";
            case "dutch": return "De leeftijd die je hebt ingevoerd is ongeldig. Geef alsjeblieft je echte leeftijd op.";
            case "german": return "Das Alter, das du eingegeben hast, ist ungültig. Bitte gib dein echtes Alter an.";
            case "italian": return "L'età che hai inserito non è valida. Per favore, fornisci la tua età reale.";
            case "tagalog": return "Ang edad na iyong inilagay ay hindi wasto. Pakibigay ang iyong totoong edad.";
            case "japanese": return "入力された年齢は無効です。実際の年齢を提供してください。";
            case "chinese": return "您输入的年龄无效。请提供您的真实年龄。";
            case "swahili": return "Umri uliouingiza si sahihi. Tafadhali toa umri wako halisi.";
            case "afrikaans": return "De leeftijd die je hebt ingevoerd is ongeldig. Geef alsjeblieft je echte leeftijd op.";
            case "romanian": return "Vârsta pe care ați introdus-o nu este validă. Vă rugăm să furnizați vârsta reală.";
            default: return "The age you entered is invalid. Please provide your real age.";
        }
    }

    public static String getUnderageWarning(String language) {
        if (language == null) return "You do not meet the minimum age requirement to apply for matchmaking.\n\n**Your application has been deleted to protect your privacy.**";
        switch (language.toLowerCase()) {
            case "spanish": return "No cumples con el requisito de edad mínima para aplicar a la búsqueda de pareja.\n\n**Tu solicitud ha sido eliminada para proteger tu privacidad.**";
            case "french": return "Vous ne remplissez pas les conditions d'âge minimum pour postuler au matchmaking.\n\n**Votre candidature a été supprimée pour protéger votre vie privée.**";
            case "portuguese": return "Você não atende ao requisito de idade mínima para se inscrever no matchmaking.\n\n**Sua inscrição foi excluída para proteger sua privacidade.**";
            case "dutch": return "Je voldoet niet aan de minimumleeftijdseis om je aan te melden voor matchmaking.\n\n**Je aanvraag is verwijderd om je privacy te beschermen.**";
            case "german": return "Du erfüllst nicht die Mindestalteranforderung, um dich für das Matchmaking zu bewerben.\n\n**Deine Bewerbung wurde gelöscht, um deine Privatsphäre zu schützen.**";
            case "italian": return "Non soddisfi il requisito di età minima per candidarti al matchmaking.\n\n**La tua candidatura è stata eliminata per proteggere la tua privacy.**";
            case "tagalog": return "Hindi mo naabot ang minimum na edad para mag-apply sa matchmaking.\n\n**Ang iyong aplikasyon ay tinanggal upang protektahan ang iyong privacy.**";
            case "japanese": return "マッチメイキングの最低年齢要件を満たしていません。\n\n**プライバシー保護のため、あなたの申請は削除されました。**";
            case "chinese": return "您未达到申请匹配的最低年龄要求。\n\n**为了保护您的隐私，您的申请已被删除。**";
            case "swahili": return "Hukidhi mahitaji ya umri wa chini ya kuomba matchmaking.\n\n**Maombi yako yamefutwa ili kulinda faragha yako.**";
            case "afrikaans": return "Je voldoet niet aan de minimumleeftijdseis om je aan te melden voor matchmaking.\n\n**Je aanvraag is verwijderd om je privacy te beschermen.**";
            case "romanian": return "Nu îndeplinești cerința de vârstă minimă pentru a aplica la matchmaking.\n\n**Aplicația ta a fost ștearsă pentru a-ți proteja intimitatea.**";
            default: return "You do not meet the minimum age requirement to apply for matchmaking.\n\n**Your application has been deleted to protect your privacy.**";
        }
    }

    public static String getLanguageConfirmation(String language) {
        if (language == null) return "No problem! We'll continue in English.\n\n**(2/15)** ";
        switch (language.toLowerCase()) {
            case "spanish": return "¡No hay problema! Continuaremos en español.\n\n**(2/15)** ";
            case "french": return "Pas de problème! Nous continuerons en français.\n\n**(2/15)** ";
            case "portuguese": return "Sem problemas! Continuaremos em português.\n\n**(2/15)** ";
            case "dutch": return "Geen probleem! We gaan verder in het Nederlands.\n\n**(2/15)** ";
            case "german": return "Kein Problem! Wir machen auf Deutsch weiter.\n\n**(2/15)** ";
            case "italian": return "Nessun problema! Continueremo in italiano.\n\n**(2/15)** ";
            case "tagalog": return "Walang problema! Magpapatuloy tayo sa Tagalog.\n\n**(2/15)** ";
            case "japanese": return "問題ありません！日本語で続けます。\n\n**(2/15)** ";
            case "chinese": return "没问题！我们将继续用中文进行。\n\n**(2/15)** ";
            case "swahili": return "Hakuna shida! Tutaendelea kwa Kiswahili.\n\n**(2/15)** ";
            case "afrikaans": return "Geen probleem nie! Ons sal in Afrikaans voortgaan.\n\n**(2/15)** ";
            case "romanian": return "Nicio problemă! Vom continua în limba română.\n\n**(2/15)** ";
            default: return "No problem! We'll continue in English.\n\n**(2/15)** ";
        }
    }

    public static String getLanguageSwitchPrompt(String language) {
        if (language == null) return "## 🌐 English is a supported language! Would you like to continue the application process in English?";
        switch (language.toLowerCase()) {
            case "spanish": return "## 🌐 ¡El español es un idioma compatible! ¿Le gustaría continuar el proceso de solicitud en español?";
            case "french": return "## 🌐 Le français est une langue prise en charge! Souhaitez-vous continuer le processus de candidature en français?";
            case "portuguese": return "## 🌐 O português é um idioma suportado! Você gostaria de continuar o processo de inscrição em português?";
            case "dutch": return "## 🌐 Nederlands is een ondersteunde taal! Wilt u het sollicitatieproces in het Nederlands voortzetten?";
            case "german": return "## 🌐 Deutsch wird unterstützt! Möchten Sie den Bewerbungsprozess auf Deutsch fortsetzen?";
            case "italian": return "## 🌐 L'italiano è supportato! Vuoi continuare il processo di candidatura in italiano?";
            case "tagalog": return "## 🌐 Sinusuportahan ang Tagalog! Gusto mo bang ipagpatuloy ang aplikasyon sa Tagalog?";
            case "japanese": return "## 🌐 日本語がサポートされています！このまま日本語で申し込みを続けますか？";
            case "chinese": return "## 🌐 支持中文！您想继续用中文完成申请吗？";
            case "swahili": return "## 🌐 Kiswahili kinatumika! Je, ungependa kuendelea na mchakato wa maombi kwa Kiswahili?";
            case "afrikaans": return "## 🌐 Afrikaans word ondersteun! Wil jy die aansoekproses in Afrikaans voortsit?";
            case "romanian": return "## 🌐 Limba română este suportată! Doriți să continuați procesul de aplicare în română?";
            default: return "## 🌐 English is a supported language! Would you like to continue the application process in English?";
        }
    }

    public static boolean isFemale(String input) {
        if (input == null) return false;
        String normalizedInput = input.trim().toLowerCase();
        return normalizedInput.equals("female") || normalizedInput.equals("f") ||
            // Spanish
            normalizedInput.equals("mujer") ||
            // French
            normalizedInput.equals("femme") ||
            // Portuguese
            normalizedInput.equals("mulher") ||
            // Dutch
            normalizedInput.equals("vrouw") ||
            // German
            normalizedInput.equals("frau") ||
            // Italian
            normalizedInput.equals("donna") ||
            // Tagalog
            normalizedInput.equals("babae") ||
            // Japanese
            normalizedInput.equals("女性") ||
            // Chinese
            normalizedInput.equals("女性") ||
            // Swahili
            normalizedInput.equals("mwanamke") ||
            // Afrikaans
            normalizedInput.equals("vrou") ||
            // Romanian
            normalizedInput.equals("femeie") ||

            // Mispellings

            // English
            normalizedInput.equals("femal") || normalizedInput.equals("femle") || normalizedInput.equals("femalw") ||
            // Spanish
            normalizedInput.equals("mujr") || normalizedInput.equals("mujre") || normalizedInput.equals("mujerw") ||
            // French
            normalizedInput.equals("femm") || normalizedInput.equals("femmee") || normalizedInput.equals("femmea") ||
            // Portuguese
            normalizedInput.equals("mulhr") || normalizedInput.equals("mulherw") || normalizedInput.equals("mulherq") ||
            // Dutch
            normalizedInput.equals("vrouw") || normalizedInput.equals("vrouq") || normalizedInput.equals("vroua") ||
            // German
            normalizedInput.equals("frauu") || normalizedInput.equals("frauw") || normalizedInput.equals("frauq") ||
            // Italian
            normalizedInput.equals("donn") || normalizedInput.equals("donnaa") || normalizedInput.equals("donnaw") ||
            // Tagalog
            normalizedInput.equals("babae") || normalizedInput.equals("babaew") || normalizedInput.equals("babaeq") ||
            // Japanese
            normalizedInput.equals("女性") || normalizedInput.equals("女性w") || normalizedInput.equals("女性q") ||
            // Chinese
            normalizedInput.equals("女性") || normalizedInput.equals("女性w") || normalizedInput.equals("女性q") ||
            // Swahili
            normalizedInput.equals("mwanamke") || normalizedInput.equals("mwanamkew") || normalizedInput.equals("mwanamkeq") ||
            // Afrikaans
            normalizedInput.equals("vrou") || normalizedInput.equals("vrouw") || normalizedInput.equals("vrouq") ||
            // Romanian
            normalizedInput.equals("femeie") || normalizedInput.equals("femeiew") || normalizedInput.equals("femeieq");
    }
}