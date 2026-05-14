package com.agape;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Base64;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public class ApplicationHandler extends ListenerAdapter {

    // Enum to represent exactly where the user is in the application process
    public enum AppStep {
        LANGUAGE,
        APPLICATION_LANGUAGE,
        NAME,
        COUNTRY,
        AGE,
        SEX,
        SECT,
        PHYSICAL,
        HOBBIES,
        STRENGTHS,
        WEAKNESSES,
        PHOTO,
        TARGET_AGE,
        TARGET_SECT,
        LOOK_FOR,
        DEAL_BREAKERS,
        CUSTOMIZE_PROMPT,
        WAITING_FOR_DESIGN_CODE,
        COMPLETED
    }

    // A simple data class to hold the user's answers as they progress
    public static class AppState {
        public AppStep currentStep = AppStep.LANGUAGE;
        
        public String language;
        public String username; // NEW: Store the Discord Handle!
        public String name;
        public String country;
        public short age;
        public String sex;
        public String sect;
        public String physicalDescription;
        public String hobbies;
        public String strengths;
        public String weaknesses;
        public String photoPath; // Local file path OR avatar URL
        public String targetAge;
        public String targetSect;
        public String lookFor;
        public String dealBreakers;
        public String designCode;
    }

    private String[] questions = {
            "What is your preferred name?\n" +
                    "-# This is the name that will appear on your profile card and that potential matches will see.",
            "What country do you live in?",
            "How old are you?",
            "What is your sex?\n" +
                    "-# (Male / Female)",
            "What is your Christian denomination?\n" +
                    "-# e.g., Baptist / Pentecostal / Non-denominational",
            "Briefly describe your physical appearance.\n" +
                    "-# e.g., \"5'7\" and slim\", \"6'3 150 lbs\"",
            "What hobbies or interests do you have that you'd like to share?\n" +
                    "-# e.g., \"hiking, cooking, playing board games\"",
            "What are a few strengths you possess?\n" +
                    "-# e.g., \"good listener, I have a great sense of humor, kind-hearted\"",
            "What are some weaknesses or areas you're working on improving?\n" +
                    "-# e.g., \"shy when first meeting, I tend to overthink things, I'm often impatient\"",
            "Please upload a picture of yourself to be used on your profile card!\n" +
                    "-# If you prefer to just use your Discord Profile Picture, type **skip**.",
            "## Great! Now that I've gotten to know you better, let's help you find a good match.\n\n" +
                    "__PROGRESS_MAP__ What age range would you consider for potential matches?\n" +
                    "-# e.g., 25-32, 18-24",
            "What Christian denominations would you consider for potential matches?\n" +
                    "-# e.g., Baptist, Pentecostal, Non-denominational",
            "What are three things you look for in a partner?\n" +
                    "-# e.g., \"kind, patient, good listener\"",
            "What are three red flags you might see in a partner?\n" +
                    "-# e.g., \"smoking, swearing, manipulative\"",
    };

    // This HashMap acts as the bot's short-term memory. Key = User ID, Value = Their AppState
    private static final Map<String, AppState> activeApplications = new HashMap<>();

    /**
     * Starts a new application for a user and sends the very first question.
     */
    public static void startApplication(User user, SlashCommandInteractionEvent event) {
        // Create a new blank state for this user and save it in memory
        AppState newState = new AppState();
        newState.username = user.getName(); // Capture their handle immediately
        activeApplications.put(user.getId(), newState);
        
        System.out.println("Started application for user: " + user.getName() + " (ID: " + user.getId() + ")");
        
        // Open a DM channel and send the very first question to kick off the chain!
        user.openPrivateChannel().queue(channel -> {
            String welcomeMessage = "## 👋 Hello! Thanks for applying to the CCM matchmaking process!\n\n" +
                                                "**(1/15)** Let's start with your preferred language. What is your first language?\n" +
                                                "-# Please enter your preferred language below.\n" +
                                                "-# Por favor, especifique su idioma preferido.\n" +
                                                "-# Veuillez indiquer votre langue préférée.\n" +
                                                "-# Por favor, especifique o seu idioma de preferência.\n" +
                                                "-# Geef alstublieft uw voorkeurstaal op.\n";
            
            // Send the message, providing both success and failure callbacks directly to queue()
            channel.sendMessage(welcomeMessage).queue(
                success -> {
                    // Only let them know in the server if the DM physically arrived
                    event.getHook().sendMessage("✅ I've sent you a DM to begin the application process!").queue();
                },
                error -> {
                    System.err.println("❌ Failed to send DM to user: " + user.getName() + " (ID: " + user.getId() + "). They might have DMs disabled.");
                    event.getHook().sendMessage("❌ I couldn't send you a DM. Please make sure your privacy settings allow direct messages from server members and try again.").queue();
                    // Clean up the application state since we can't proceed without DM access
                    activeApplications.remove(user.getId());
                }
            );
            
        }, error -> {
            // This catches if Discord blocks us from even opening the channel
            System.err.println("❌ Failed to open DM channel for user: " + user.getName() + " (ID: " + user.getId() + ")");
            event.getHook().sendMessage("❌ I couldn't open a DM with you. Please make sure your DMs are open and try again.").queue();
            activeApplications.remove(user.getId());
        });
    }

    private boolean supportedLanguage(String language) {
        String[] supportedLanguages = {"english", "spanish", "french", "portuguese", "dutch", "german", "italian", "tagalog", "japanese", "chinese", "swahili", "afrikaans", "romanian"};
        for (String lang : supportedLanguages) {
            if (lang.equalsIgnoreCase(language)) {
                return true;
            }
        }
        return false;
    }

    private String getLanguageName(String input) {
        if (input == null) return null;
        input = input.trim().toLowerCase();
        switch (input) {
            case "english":
            case "en":
            case "inglés":
            case "anglais":
            case "inglês":
            case "engels":
                return "english";
            case "spanish":
            case "español":
            case "es":
                return "spanish";
            case "french":
            case "français":
            case "fr":
                return "french";
            case "portuguese":
            case "português":
            case "pt":
                return "portuguese";
            case "dutch":
            case "nederlands":
            case "nl":
                return "dutch";
            case "german":
            case "deutsch":
            case "de":
                return "german";
            case "italian":
            case "italiano":
            case "it":
                return "italian";
            case "tagalog":
            case "filipino":
            case "tl":
                return "tagalog";
            case "japanese":
            case "nihongo":
            case "日本語":
            case "ja":
                return "japanese";
            case "chinese":
            case "mandarin":
            case "zhongwen":
            case "中文":
            case "zh":
                return "chinese";
            case "swahili":
            case "kiswahili":
            case "sw":
                return "swahili";
            case "afrikaans":
            case "af":
                return "afrikaans";
            case "romanian":
                return "romanian";
            default:
                return input;
        }
    }

    private boolean isYes(String input) {
        input = input.trim().toLowerCase();
        return input.matches("^(yes|y|sí|si|oui|sim|ja|oo|opo|sì|hai|はい|shi|是|ndiyo|da)$");
    }

    private boolean isNo(String input) {
        input = input.trim().toLowerCase();
        return input.matches("^(no|n|non|não|nao|nee|nein|hindi|iie|いいえ|bu|不|hapana|nu)$");
    }

    private boolean isCancel(String input) {
        input = input.trim().toLowerCase();
        return input.matches("^(cancel|cancelar|annuler|annuleren|abbrechen|annulla|kanselahin|キャンセル|取消|ghairi|kanselleer|anulează)$");
    }

    private String getGeneratingMessage(String language) {
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

    private String getCustomizationPrompt(String language) {
        if (language == null) return "🎨 Would you like to customize the look of your card before submitting?\n-# Reply **yes** to customize, or **no** to submit your application as-is.";
        switch (language.toLowerCase()) {
            case "spanish": return "🎨 ¿Te gustaría personalizar el diseño de tu tarjeta antes de enviarla?\n-# Responde **sí** para personalizar, o **no** para enviarla tal como está.";
            case "french": return "🎨 Souhaitez-vous personnaliser l'apparence de votre carte avant de la soumettre?\n-# Répondez **oui** pour personnaliser, ou **non** pour la soumettre telle quelle.";
            case "portuguese": return "🎨 Você gostaria de personalizar a aparência do seu cartão antes de enviar?\n-# Responda **sim** para personalizar, ou **não** para enviar como está.";
            case "dutch": return "🎨 Wil je het uiterlijk van je kaart aanpassen voordat je deze indient?\n-# Antwoord **ja** om aan te passen, of **nee** om in te dienen zoals deze is.";
            case "german": return "🎨 Möchtest du das Aussehen deiner Karte vor dem Einreichen anpassen?\n-# Antworte mit **ja** zum Anpassen oder **nein**, um sie so einzureichen.";
            case "italian": return "🎨 Vuoi personalizzare l'aspetto della tua scheda prima di inviarla?\n-# Rispondi **sì** per personalizzare, o **no** per inviarla così com'è.";
            case "tagalog": return "🎨 Gusto mo bang i-customize ang hitsura ng iyong card bago isumite?\n-# Sumagot ng **oo** para i-customize, o **hindi** para isumite nang ganito.";
            case "japanese": return "🎨 提出する前にカードのデザインをカスタマイズしますか？\n-# カスタマイズする場合は **はい** 、このまま提出する場合は **いいえ** と返信してください。";
            case "chinese": return "🎨 您想在提交前自定义卡片的外观吗？\n-# 回复 **是** 进行自定义，或回复 **否** 直接提交。";
            case "swahili": return "🎨 Je, ungependa kubadilisha mwonekano wa kadi yako kabla ya kuwasilisha?\n-# Jibu **ndiyo** kubadilisha, au **hapana** kuwasilisha jinsi ilivyo.";
            case "afrikaans": return "🎨 Wil jy die voorkoms van jou kaart aanpas voordat jy dit indien?\n-# Antwoord **ja** om aan te pas, of **nee** om dit soos dit is in te dien.";
            case "romanian": return "🎨 Doriți să personalizați aspectul cardului înainte de a trimite?\n-# Răspundeți **da** pentru a personaliza, sau **nu** pentru a trimite așa cum este.";
            default: return "🎨 Would you like to customize the look of your card before submitting?\n-# Reply **yes** to customize, or **no** to submit your application as-is.";
        }
    }

    private String getDesignCodePrompt(String language, String userId) {
        // Base64 encode the user ID to obscure it in the web URL
        String encodedId = Base64.getEncoder().encodeToString(userId.getBytes());
        String url = "https://eminich.com/apps/ccm/?id=" + encodedId;

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

    private String getYesNoWarning(String language) {
        if (language == null) return "Please answer with yes or no.";
        switch (language.toLowerCase()) {
            case "spanish": return "Por favor responda con 'sí' o 'no'.";
            case "french": return "Veuillez répondre par 'oui' ou 'non'.";
            case "portuguese": return "Por favor responda com 'sim' ou 'não'.";
            case "dutch": return "Beantwoord alstublieft met 'ja' of 'nee'.";
            case "german": return "Bitte antworte mit 'ja' oder 'nein'.";
            case "italian": return "Rispondi con 'sì' o 'no'.";
            case "tagalog": return "Pakisagot ng 'oo' o 'hindi'.";
            case "japanese": return "「はい」または「いいえ」で答えてください。";
            case "chinese": return "请回答“是”或“否”。";
            case "swahili": return "Tafadhali jibu 'ndiyo' au 'hapana'.";
            case "afrikaans": return "Antwoord asseblief met 'ja' of 'nee'.";
            case "romanian": return "Vă rugăm să răspundeți cu 'da' sau 'nu'.";
            default: return "Please answer with yes or no.";
        }
    }

    private String getCompletionMessage(String language) {
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

    private String getInvalidAgeWarning(String language) {
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

    private String getUnderageWarning(String language) {
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

    private void completeApplication(AppState state, String userId, MessageReceivedEvent event) {
        state.currentStep = AppStep.COMPLETED;
        activeApplications.remove(userId);
        
        event.getChannel().sendMessage("✅ **" + getCompletionMessage(state.language) + "**").queue();
        
        File profilesDir = new File("user_content/profiles/");
        if (!profilesDir.exists()) {
            profilesDir.mkdirs();
        }

        File profileFile = new File(profilesDir, userId + ".json");
        try (FileWriter writer = new FileWriter(profileFile)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(state, writer);
            System.out.println("✅ Saved application data for " + state.name + " to " + profileFile.getPath());
        } catch (IOException e) {
            System.err.println("❌ Failed to save profile JSON for user: " + userId);
            e.printStackTrace();
        }

        // Clean up the temporary srv file if it exists so it isn't publicly accessible
        File srvFile = new File("user_content/srv/" + userId + ".json");
        if (srvFile.exists()) {
            if (srvFile.delete()) {
                System.out.println("🧹 Cleaned up temporary srv data for user: " + userId);
            } else {
                System.err.println("⚠️ Failed to clean up temporary srv data for user: " + userId);
            }
        }
    }

    private String switchLanguage(String language) {
        switch (language.toLowerCase()) {
            case "spanish":
                questions = new String[] {
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
                return "¡No hay problema! Continuaremos en español.\n\n";
            case "french":
                questions = new String[] {
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
                return "Pas de problème! Nous continuerons en français.\n\n";
            case "portuguese":
                questions = new String[] {
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
                return "Sem problemas! Continuaremos em português.\n\n";
            case "dutch":
                questions = new String[] {
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
                return "Geen probleem! We gaan verder in het Nederlands.\n\n";
            case "german":
                questions = new String[] {
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
                return "Kein Problem! Wir machen auf Deutsch weiter.\n\n";
            case "italian":
                questions = new String[] {
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
                return "Nessun problema! Continueremo in italiano.\n\n";
            case "tagalog":
                questions = new String[] {
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
                return "Walang problema! Magpapatuloy tayo sa Tagalog.\n\n";
            case "japanese":
                questions = new String[] {
                    "どのようにお呼びすればよろしいですか？\n-# これはプロフィールカードに表示される名前です。",
                    "お住まいの国はどこですか？",
                    "年齢を教えてください。",
                    "性別を教えてください。\n-# (男性 / 女性)",
                    "あなたのキリスト教の教派は何ですか？\n-# 例：バプテスト / ペンテコステ派 / 無教派",
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
                return "問題ありません！日本語で続けます。\n\n";
            case "chinese":
                questions = new String[] {
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
                return "没问题！我们将继续用中文进行。\n\n";
            case "swahili":
                questions = new String[] {
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
                return "Hakuna shida! Tutaendelea kwa Kiswahili.\n\n";
            case "afrikaans":
                questions = new String[] {
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
                return "Geen probleem nie! Ons sal in Afrikaans voortgaan.\n\n";
            case "romanian":
                questions = new String[] {
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
                return "Nicio problemă! Vom continua în limba română.\n\n";
            default:
                return "No problem! We'll continue in English.\n\n";
        }
    }

    private String toTitleCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // 1. Ignore messages from bots (including ourselves)
        if (event.getAuthor().isBot()) return;

        // 2. We only care about Direct Messages for the application
        if (event.isFromGuild()) return;

        User user = event.getAuthor();
        String userId = user.getId();

        // 3. Check if this user actually has an active application going on
        if (!activeApplications.containsKey(userId)) return;

        AppState state = activeApplications.get(userId);
        String messageContent = event.getMessage().getContentRaw().trim();

        // Process the answer based on what step they are currently on
        switch (state.currentStep) {
            case LANGUAGE:
                state.language = getLanguageName(messageContent);
                if (!supportedLanguage(state.language) || state.language.equalsIgnoreCase("english") || state.language.equalsIgnoreCase("en") || state.language.equalsIgnoreCase("inglés") || state.language.equalsIgnoreCase("anglais") || state.language.equalsIgnoreCase("inglês") || state.language.equalsIgnoreCase("engels")) {
                    state.currentStep = AppStep.NAME;
                    event.getChannel().sendMessage("**(2/15)** " + questions[0]).queue();
                } else {
                    state.currentStep = AppStep.APPLICATION_LANGUAGE;
                    String prepend = "## 🌐 English is a supported language! Would you like to continue the application process in English?";
                    switch (state.language) {
                        case "spanish":
                            prepend = "## 🌐 ¡El español es un idioma compatible! ¿Le gustaría continuar el proceso de solicitud en español?";
                            break;
                        case "french":
                            prepend = "## 🌐 Le français est une langue prise en charge! Souhaitez-vous continuer le processus de candidature en français?";
                            break;
                        case "portuguese":
                            prepend = "## 🌐 O português é um idioma suportado! Você gostaria de continuar o processo de inscrição em português?";
                            break;
                        case "dutch":
                            prepend = "## 🌐 Nederlands is een ondersteunde taal! Wilt u het sollicitatieproces in het Nederlands voortzetten?";
                            break;
                        case "german":
                            prepend = "## 🌐 Deutsch wird unterstützt! Möchten Sie den Bewerbungsprozess auf Deutsch fortsetzen?";
                            break;
                        case "italian":
                            prepend = "## 🌐 L'italiano è supportato! Vuoi continuare il processo di candidatura in italiano?";
                            break;
                        case "tagalog":
                            prepend = "## 🌐 Sinusuportahan ang Tagalog! Gusto mo bang ipagpatuloy ang aplikasyon sa Tagalog?";
                            break;
                        case "japanese":
                            prepend = "## 🌐 日本語がサポートされています！このまま日本語で申し込みを続けますか？";
                            break;
                        case "chinese":
                            prepend = "## 🌐 支持中文！您想继续用中文完成申请吗？";
                            break;
                        case "swahili":
                            prepend = "## 🌐 Kiswahili kinatumika! Je, ungependa kuendelea na mchakato wa maombi kwa Kiswahili?";
                            break;
                        case "afrikaans":
                            prepend = "## 🌐 Afrikaans word ondersteun! Wil jy die aansoekproses in Afrikaans voortsit?";
                            break;
                        case "romanian":
                            prepend = "## 🌐 Limba română este suportată! Doriți să continuați procesul de aplicare în română?";
                            break;
                        default:
                            break;
                    }
                    event.getChannel().sendMessage(prepend + "\n-# Would you like to continue the application in " + toTitleCase(state.language) + "?").queue();
                }
                break;
            
            case APPLICATION_LANGUAGE:
                if (isYes(messageContent)) {
                    String prepend = switchLanguage(state.language);
                    event.getChannel().sendMessage(prepend + questions[0]).queue();
                    state.currentStep = AppStep.NAME;
                } else if (isNo(messageContent)) {
                    state.language = "English";
                    event.getChannel().sendMessage("No problem! We'll continue in English.\n\n**(2/15)** " + questions[0]).queue();
                    state.currentStep = AppStep.NAME;
                } else {
                    event.getChannel().sendMessage("⚠️ " + getYesNoWarning(state.language) + "\n-# Please answer with yes or no.").queue();
                }
                break;

            case NAME:
                state.name = messageContent;
                state.currentStep = AppStep.COUNTRY;
                event.getChannel().sendMessage("**(3/15)** " + questions[1]).queue();
                break;

            case COUNTRY:
                state.country = messageContent;
                state.currentStep = AppStep.AGE;
                event.getChannel().sendMessage("**(4/15)** " + questions[2]).queue();
                break;

            case AGE:
                try {
                    state.age = Short.parseShort(messageContent);
                    if (state.age > 99) {
                        event.getChannel().sendMessage("⚠️ " + getInvalidAgeWarning(state.language) + "\n-# Please enter a valid number for your age.").queue();
                        return;
                    } else if (state.age < 18) {
                        event.getChannel().sendMessage("❌ " + getUnderageWarning(state.language)).queue();
                        activeApplications.remove(userId);
                        return;
                    }
                    state.currentStep = AppStep.SEX;
                    event.getChannel().sendMessage("**(5/15)** " + questions[3]).queue();
                } catch (NumberFormatException e) {
                    event.getChannel().sendMessage("⚠️ " + getInvalidAgeWarning(state.language) + "\n-# Please enter a valid number for your age.").queue();
                    return;
                }
                break;

            case SEX:
                state.sex = messageContent;
                state.currentStep = AppStep.SECT;
                event.getChannel().sendMessage("**(6/15)** " + questions[4]).queue();
                break;

            case SECT:
                state.sect = messageContent;
                state.currentStep = AppStep.PHYSICAL;
                event.getChannel().sendMessage("**(7/15)** " + questions[5]).queue();
                break;

            case PHYSICAL:
                state.physicalDescription = messageContent;
                state.currentStep = AppStep.HOBBIES;
                event.getChannel().sendMessage("**(8/15)** " + questions[6]).queue();
                break;

            case HOBBIES:
                state.hobbies = messageContent;
                state.currentStep = AppStep.STRENGTHS;
                event.getChannel().sendMessage("**(9/15)** " + questions[7]).queue();
                break;

            case STRENGTHS:
                state.strengths = messageContent;
                state.currentStep = AppStep.WEAKNESSES;
                event.getChannel().sendMessage("**(10/15)** " + questions[8]).queue();
                break;

            case WEAKNESSES:
                state.weaknesses = messageContent;
                state.currentStep = AppStep.PHOTO;
                event.getChannel().sendMessage("**(11/15)** " + questions[9]).queue();
                break;

            case PHOTO:
                // Check if they opted to skip
                if (messageContent.equalsIgnoreCase("skip") || messageContent.equalsIgnoreCase("no")) {
                    state.photoPath = user.getEffectiveAvatarUrl();
                    advanceToTargetAge(state, event);
                } 
                // Check if they actually attached an image
                else if (!event.getMessage().getAttachments().isEmpty()) {
                    Message.Attachment attachment = event.getMessage().getAttachments().get(0);
                    
                    // Verify it's an image
                    if (attachment.isImage()) {
                        // Ensure the directory exists
                        File directory = new File("user_content/images/");
                        if (!directory.exists()) {
                            directory.mkdirs();
                        }

                        // Save the file as their User ID + extension (e.g., 123456.png)
                        String extension = attachment.getFileExtension();
                        File destFile = new File(directory, userId + "." + extension);
                        
                        // Download the file from Discord's servers
                        attachment.getProxy().downloadToFile(destFile).thenAccept(file -> {
                            state.photoPath = file.getAbsolutePath();
                            advanceToTargetAge(state, event);
                        }).exceptionally(ex -> {
                            event.getChannel().sendMessage("❌ Something went wrong saving your image. We'll use your profile picture instead.").queue();
                            state.photoPath = user.getEffectiveAvatarUrl();
                            advanceToTargetAge(state, event);
                            return null;
                        });
                    } else {
                        event.getChannel().sendMessage("⚠️ That attachment doesn't look like an image. Please upload a picture or type **skip**.").queue();
                    }
                } else {
                    event.getChannel().sendMessage("⚠️ Please upload an image file, or type **skip** to use your Discord avatar.").queue();
                }
                break;

            case TARGET_AGE:
                state.targetAge = messageContent;
                state.currentStep = AppStep.TARGET_SECT;
                event.getChannel().sendMessage("**(13/15)** " + questions[11]).queue();
                break;

            case TARGET_SECT:
                state.targetSect = messageContent;
                state.currentStep = AppStep.LOOK_FOR;
                event.getChannel().sendMessage("**(14/15)** " + questions[12]).queue();
                break;

            case LOOK_FOR:
                state.lookFor = messageContent;
                state.currentStep = AppStep.DEAL_BREAKERS;
                event.getChannel().sendMessage("**(15/15)** " + questions[13]).queue();
                break;

            case DEAL_BREAKERS:
                state.dealBreakers = messageContent;
                state.currentStep = AppStep.CUSTOMIZE_PROMPT;
                
                event.getChannel().sendMessage("⏳ " + getGeneratingMessage(state.language)).queue(loadingMsg -> {
                    new Thread(() -> {
                        try {
                            // Ensure the image path is a valid URI if it's a local file
                            String pfpUri = state.photoPath.startsWith("http") ? state.photoPath : new File(state.photoPath).toURI().toURL().toString();

                            int currYear = Calendar.getInstance().get(Calendar.YEAR);

                            String strAndWeak = "\n";

                            if (state.strengths != null && !state.strengths.isEmpty() && state.weaknesses != null && !state.weaknesses.isEmpty()) {
                                strAndWeak = state.strengths + "\n" + state.weaknesses + "\n\n";
                            }
                            
                            // Construct the beautiful rich text using their actual answers!
                            String text = "{blob}{s:70}*{g:line:#FF6699:#9966FF}{o:#FFFFFF:10.0}{f:Arial Rounded MT Bold}" + state.name + "{/}*\n" +
                                          "{blob}{s:45}*{g:line:#FF6699:#FF9966}{o:#FFFFFF:8.0}{f:Arial Rounded MT Bold}@" + user.getName() + "{/}*\n\n" +
                                          // Get age and also subtract if from current year
                                          state.age + " | " + (currYear - state.age) + "\n" +
                                          state.sex + "\n" +
                                          state.sect + "\n" +
                                          state.physicalDescription + "\n\n" +
                                          state.hobbies + "\n\n" +
                                          strAndWeak +
                                          "{img:green_flag.png} PARTNER: " + state.lookFor.replace("\n", ", ") + "\n" +
                                          "{img:red_flag.png} PARTNER: " + state.dealBreakers.replace("\n", ", ");
            
                            String bgPath = "assets/backgrounds/default.png";
                            String framePath = "assets/frames/default.png";
                            String fontPath = "assets/fonts/VAG Rounded Next Shine Regular.ttf";
            
                            File generatedImage = ImageGenerator.generateForUser(bgPath, pfpUri, framePath, fontPath, text, userId);
            
                            if (generatedImage != null && generatedImage.exists()) {
                                event.getChannel().sendFiles(net.dv8tion.jda.api.utils.FileUpload.fromData(generatedImage)).queue(success -> {
                                    generatedImage.delete();
                                    event.getChannel().sendMessage(getCustomizationPrompt(state.language)).queue();
                                }, error -> {
                                    System.err.println("Failed to send preview image: " + error.getMessage());
                                    generatedImage.delete();
                                    event.getChannel().sendMessage(getCustomizationPrompt(state.language)).queue();
                                });
                            } else {
                                event.getChannel().sendMessage("⚠️ Failed to generate preview.\n\n" + getCustomizationPrompt(state.language)).queue();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            event.getChannel().sendMessage("⚠️ Error generating preview.\n\n" + getCustomizationPrompt(state.language)).queue();
                        }
                    }).start();
                });
                break;

            case CUSTOMIZE_PROMPT:
                if (isYes(messageContent)) {
                    state.currentStep = AppStep.WAITING_FOR_DESIGN_CODE;
                    event.getChannel().sendMessage(getDesignCodePrompt(state.language, userId)).queue();
                    
                    // Save temporary JSON to the 'srv' directory for the web app to access!
                    File srvDir = new File("user_content/srv/");
                    if (!srvDir.exists()) {
                        srvDir.mkdirs();
                    }

                    File srvFile = new File(srvDir, userId + ".json");
                    try (FileWriter writer = new FileWriter(srvFile)) {
                        Gson gson = new GsonBuilder().setPrettyPrinting().create();
                        gson.toJson(state, writer);
                        System.out.println("✅ Saved temporary application data for " + state.name + " to " + srvFile.getPath());
                    } catch (IOException e) {
                        System.err.println("❌ Failed to save temp profile JSON for user: " + userId);
                        e.printStackTrace();
                    }
                } else if (isNo(messageContent)) {
                    completeApplication(state, userId, event);
                } else {
                    event.getChannel().sendMessage("⚠️ " + getYesNoWarning(state.language)).queue();
                }
                break;
                
            case WAITING_FOR_DESIGN_CODE:
                if (isCancel(messageContent)) {
                    state.currentStep = AppStep.CUSTOMIZE_PROMPT;
                    event.getChannel().sendMessage(getCustomizationPrompt(state.language)).queue();
                } else {
                    state.designCode = messageContent;
                    completeApplication(state, userId, event);
                }
                break;
        }
    }

    // Helper method to progress from Photo to Target Age, since it can be triggered from multiple branches above
    private void advanceToTargetAge(AppState state, MessageReceivedEvent event) {
        state.currentStep = AppStep.TARGET_AGE;
        event.getChannel().sendMessage(questions[10].replaceAll("__PROGRESS_MAP__", "**(12/15)**")).queue();
    }
}