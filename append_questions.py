import json

# Poll positions within new block (0-indexed).
# Stream C (year%4==2) polls: 18,36,54,70,92,110,128,146,164,184,204,222,240,260,278,296,316,334
# Stream D (year%4==3) polls: 19,37,55,75,93,111,129,147,167,185,205,223,241,261,279,297,317,335
POLL_POSITIONS = {
    18,19,36,37,54,55,70,75,92,93,110,111,128,129,146,147,
    164,167,184,185,204,205,222,223,240,241,260,261,278,279,
    296,297,316,317,334,335
}

new_questions = [
    # 0
    {"category":"Dating & Marriage","format":"QA","question":"What is one expectation about a future spouse that you have never admitted out loud to anyone?"},
    # 1
    {"category":"Dating & Marriage","format":"QA","question":"How do you tell the difference between feelings worth pursuing and feelings that are just appreciation?"},
    # 2
    {"category":"Christian Faith","format":"QA","question":"What does it mean to fear the Lord in a way that draws you closer to Him rather than pushing you away?"},
    # 3
    {"category":"Life & Character","format":"QA","question":"How do you handle the discomfort of knowing you need to change but not yet wanting to?"},
    # 4
    {"category":"Dating & Marriage","format":"QA","question":"What is one quality in a potential spouse that you have become less willing to compromise on over time?"},
    # 5
    {"category":"Fun & Icebreakers","format":"QA","question":"If you could have dinner with any three people — living or dead — who would they be and what would you ask?"},
    # 6
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to you to fight for a relationship rather than fight in it?"},
    # 7
    {"category":"Christian Faith","format":"QA","question":"How do you think about the tension between God's sovereignty and human responsibility in your own life choices?"},
    # 8
    {"category":"Life & Character","format":"QA","question":"What is one area where you know you are harder on yourself than God is?"},
    # 9
    {"category":"Family & Children","format":"QA","question":"How do you think about the balance between a parent's authority and a child's growing independence?"},
    # 10
    {"category":"Dating & Marriage","format":"QA","question":"What does it look like to protect someone's heart while still pursuing them with clarity?"},
    # 11
    {"category":"Christian Faith","format":"QA","question":"How has your understanding of God's grace changed the way you extend grace to others?"},
    # 12
    {"category":"Dating & Marriage","format":"QA","question":"What is one way that your love language has changed or evolved as you have gotten older?"},
    # 13
    {"category":"Life & Character","format":"QA","question":"How do you cultivate genuine humility when success makes it easy to become proud?"},
    # 14
    {"category":"Dating & Marriage","format":"QA","question":"What do you think is the biggest difference between a healthy friendship and a romantic relationship?"},
    # 15
    {"category":"Culture & Society","format":"QA","question":"What does it mean to be a good neighbor in a society where people rarely know their neighbors?"},
    # 16
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about being in a relationship that you think would surprise you most?"},
    # 17
    {"category":"Fun & Icebreakers","format":"QA","question":"What is something you believed confidently at 20 that you have since completely reversed on?"},
    # 18 — POLL (Stream C)
    {"category":"Dating & Marriage","format":"Poll","question":"Would you pursue a relationship with someone if key mentors in your life strongly cautioned against it?","options":["No — I trust their discernment over my feelings","Probably not — I would take it very seriously","Maybe — it would depend on their reasoning","Yes — I would ultimately trust my own judgment"]},
    # 19 — POLL (Stream D)
    {"category":"Dating & Marriage","format":"Poll","question":"Is it possible to love someone deeply and genuinely and still not be right for them?","options":["Yes, absolutely — love is not always enough","Probably yes — it is painful but possible","Uncertain — it is hard to imagine in practice","No — true love finds a way"]},
    # 20
    {"category":"Christian Faith","format":"QA","question":"What does your prayer life reveal about what you actually believe about God?"},
    # 21
    {"category":"Dating & Marriage","format":"QA","question":"How do you balance having clear standards for a future spouse with staying genuinely open?"},
    # 22
    {"category":"Life & Character","format":"QA","question":"What does it look like to pursue excellence without making your identity depend on it?"},
    # 23
    {"category":"Dating & Marriage","format":"QA","question":"What is one habit you would need to change to be the partner you want to be?"},
    # 24
    {"category":"Fun & Icebreakers","format":"QA","question":"What is one thing that consistently makes you laugh at yourself?"},
    # 25
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about the difference between a healthy dependence on a partner and an unhealthy one?"},
    # 26
    {"category":"Christian Faith","format":"QA","question":"What does it mean to you that Jesus is both fully human and fully divine — and why does that matter practically?"},
    # 27
    {"category":"Life & Character","format":"QA","question":"How do you know when you are being resilient versus just suppressing something that needs to be felt?"},
    # 28
    {"category":"Dating & Marriage","format":"QA","question":"What is one truth about yourself in relationships that took you embarrassingly long to see?"},
    # 29
    {"category":"Family & Children","format":"QA","question":"What would you want your children to understand about money and generosity before they leave your home?"},
    # 30
    {"category":"Dating & Marriage","format":"QA","question":"How do you approach the idea that the person you marry will eventually see the worst version of you?"},
    # 31
    {"category":"Christian Faith","format":"QA","question":"How do you engage with parts of Scripture that are deeply uncomfortable or culturally offensive?"},
    # 32
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to be ready for a relationship — and who decides?"},
    # 33
    {"category":"Life & Character","format":"QA","question":"How do you handle seasons when you feel behind your peers in life or faith?"},
    # 34
    {"category":"Dating & Marriage","format":"QA","question":"What is one boundary in dating that you think is more important than most people realize?"},
    # 35
    {"category":"Culture & Society","format":"QA","question":"How do you think about the Christian's responsibility to the poor and marginalized in society?"},
    # 36 — POLL (Stream C)
    {"category":"Christian Faith","format":"Poll","question":"Do you believe God speaks personally and specifically into your decisions today — not just through Scripture in general, but directly?","options":["Yes — I experience this regularly","Probably yes — though I hold it with some discernment","Uncertain — I am still working through this","No — I think He primarily speaks through Scripture and wisdom"]},
    # 37 — POLL (Stream D)
    {"category":"Life & Character","format":"Poll","question":"Would you describe yourself as someone who generally trusts people until they give you a reason not to?","options":["Yes — trust is my default starting point","Mostly yes — with some natural caution","Somewhat — I tend to take my time","No — trust is something people have to earn with me"]},
    # 38
    {"category":"Dating & Marriage","format":"QA","question":"What is one moment in a past relationship — good or bad — that permanently changed how you see love?"},
    # 39
    {"category":"Fun & Icebreakers","format":"QA","question":"What is something most people do out of social pressure that you have stopped doing?"},
    # 40
    {"category":"Dating & Marriage","format":"QA","question":"How would you handle it if your partner felt significantly closer to God than you in a particular season?"},
    # 41
    {"category":"Christian Faith","format":"QA","question":"What does it mean to worship in spirit and in truth — and do you know the difference in practice?"},
    # 42
    {"category":"Life & Character","format":"QA","question":"What is one area of your character where you have noticed genuine, measurable growth in the last two years?"},
    # 43
    {"category":"Dating & Marriage","format":"QA","question":"How do you feel about the concept of dating with a clear exit strategy — knowing in advance when you would walk away?"},
    # 44
    {"category":"Family & Children","format":"QA","question":"How do you think about the role of extended family in a couple's major decisions?"},
    # 45
    {"category":"Dating & Marriage","format":"QA","question":"What does healthy emotional intimacy look like — and where do you think most couples get it wrong?"},
    # 46
    {"category":"Christian Faith","format":"QA","question":"How do you think about the relationship between sanctification and personal effort — is becoming holy something you do or something God does?"},
    # 47
    {"category":"Life & Character","format":"QA","question":"What is one relationship in your life that consistently brings out the best in you?"},
    # 48
    {"category":"Dating & Marriage","format":"QA","question":"Is there a season in your past you feel you are still recovering from — and how honest are you about that with people close to you?"},
    # 49
    {"category":"Fun & Icebreakers","format":"QA","question":"What is one question you would ask God if you could receive a direct, honest answer right now?"},
    # 50
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to pursue someone with your head, your heart, and your faith all pointing in the same direction?"},
    # 51
    {"category":"Christian Faith","format":"QA","question":"What does it mean to be a peacemaker — and when does it require courage rather than just kindness?"},
    # 52
    {"category":"Life & Character","format":"QA","question":"What is one area where you need more courage in your everyday life?"},
    # 53
    {"category":"Dating & Marriage","format":"QA","question":"How important is it to you that a future partner has done significant work through their family-of-origin wounds?"},
    # 54 — POLL (Stream C)
    {"category":"Dating & Marriage","format":"Poll","question":"Do you believe attraction can grow meaningfully over time, or is the initial chemistry mostly fixed?","options":["Yes — attraction can deepen significantly","Mostly yes — but there is a baseline that matters","Somewhat — I do not think it changes much","No — chemistry is mostly immediate and either there or not"]},
    # 55 — POLL (Stream C)
    {"category":"Christian Faith","format":"Poll","question":"Do you believe God still performs miracles in the same way He did in Scripture?","options":["Yes — I have seen or experienced this","Probably yes — though with appropriate discernment","Uncertain — I hold this question openly","No — I think miracles operated in a distinct biblical era"]},
    # 56
    {"category":"Dating & Marriage","format":"QA","question":"What do you think about having a close opposite-sex friendship after you are married — healthy or risky?"},
    # 57
    {"category":"Life & Character","format":"QA","question":"How do you approach a situation where someone you love is making a decision you believe will harm them?"},
    # 58
    {"category":"Dating & Marriage","format":"QA","question":"What does it look like to honor someone's dignity while also being honest about incompatibility?"},
    # 59
    {"category":"Family & Children","format":"QA","question":"How do you handle the fear of repeating the mistakes of the generation before you?"},
    # 60
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing you would tell a younger version of yourself about what to actually look for in a partner?"},
    # 61
    {"category":"Christian Faith","format":"QA","question":"How do you think about the gift of the church when it has also been a source of hurt for you or someone you love?"},
    # 62
    {"category":"Life & Character","format":"QA","question":"What is one thing you need someone to know about you before they can really know you?"},
    # 63
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to love someone in a way that leads them closer to God rather than just closer to you?"},
    # 64
    {"category":"Fun & Icebreakers","format":"QA","question":"What is a season of your life that was hard at the time but that you now see as one of the most important?"},
    # 65
    {"category":"Dating & Marriage","format":"QA","question":"What is one pattern from your past relationships that you would most want to break in the next one?"},
    # 66
    {"category":"Christian Faith","format":"QA","question":"How does the resurrection of Jesus change how you face uncertainty about your own future?"},
    # 67
    {"category":"Life & Character","format":"QA","question":"How do you think about the difference between being driven and being called?"},
    # 68
    {"category":"Dating & Marriage","format":"QA","question":"What does long-term love look like when feelings come and go — what actually holds it together?"},
    # 69
    {"category":"Culture & Society","format":"QA","question":"What do you think Christians uniquely offer to conversations about justice and mercy in society?"},
    # 70 — POLL (Stream C)
    {"category":"Dating & Marriage","format":"Poll","question":"Would you be willing to relocate for a relationship if everything else seemed right?","options":["Yes — if they are the right person, absolutely","Probably — with prayer and real discernment","Uncertain — it would depend on many factors","Probably not — my roots and context are too important"]},
    # 71
    {"category":"Dating & Marriage","format":"QA","question":"What do you believe is the single most underrated quality in a marriage partner?"},
    # 72
    {"category":"Christian Faith","format":"QA","question":"What does it mean to love God with all your strength — not just heart, soul, and mind?"},
    # 73
    {"category":"Life & Character","format":"QA","question":"What is one thing you have realized about yourself that took a painful circumstance to reveal?"},
    # 74
    {"category":"Dating & Marriage","format":"QA","question":"What role do you think timing plays in whether a relationship actually works?"},
    # 75 — POLL (Stream D)
    {"category":"Life & Character","format":"Poll","question":"Do you generally find it easy to open up emotionally with people you are close to?","options":["Yes — vulnerability comes naturally to me","Mostly yes — with the right people it flows","Somewhat — it takes real time and trust","No — opening up is genuinely difficult for me"]},
    # 76
    {"category":"Christian Faith","format":"QA","question":"What does it mean to you that God is both perfectly just and perfectly merciful at the same time?"},
    # 77
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing you would need a partner to understand about how you handle conflict before you got serious?"},
    # 78
    {"category":"Life & Character","format":"QA","question":"What is one way your perspective on success has shifted as you have gotten older?"},
    # 79
    {"category":"Dating & Marriage","format":"QA","question":"How do you handle a situation where you are drawn to someone but genuinely uncertain about the timing?"},
    # 80
    {"category":"Fun & Icebreakers","format":"QA","question":"What is one experience that made you realize you had more strength than you thought you did?"},
    # 81
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to you to choose a partner with your future children already in mind?"},
    # 82
    {"category":"Christian Faith","format":"QA","question":"How do you think about the relationship between faith and emotions — when they disagree, which do you trust?"},
    # 83
    {"category":"Life & Character","format":"QA","question":"What is one thing you consistently do that genuinely reflects who you want to be?"},
    # 84
    {"category":"Dating & Marriage","format":"QA","question":"What is the most dangerous assumption people make about what marriage will fix in their life?"},
    # 85
    {"category":"Family & Children","format":"QA","question":"How do you think about building family traditions — are they important or a potential source of pressure?"},
    # 86
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about the concept of emotional baggage — do you have some, and what do you actually do with it?"},
    # 87
    {"category":"Christian Faith","format":"QA","question":"What does it mean to hold your theology with conviction while holding your posture with genuine humility?"},
    # 88
    {"category":"Life & Character","format":"QA","question":"What does it look like to be genuinely humble in a culture that rewards confidence and self-promotion?"},
    # 89
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about you that someone who loves you deeply would need to be consistently patient with?"},
    # 90
    {"category":"Fun & Icebreakers","format":"QA","question":"What is one skill you have that you believe is genuinely useful in building a strong community?"},
    # 91
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about the concept of spiritual warfare in the context of a relationship?"},
    # 92 — POLL (Stream C)
    {"category":"Dating & Marriage","format":"Poll","question":"Do you think it is possible to know someone is right for you within the first few months of dating?","options":["Yes — you can know quickly with wisdom and discernment","Probably — but it needs more time to confirm","Unlikely — real depth takes longer than that","No — you need at least a year to really know someone"]},
    # 93 — POLL (Stream D)
    {"category":"Christian Faith","format":"Poll","question":"Do you believe that God has a specific, detailed plan for your life — not just a general will?","options":["Yes — He is that personal and specific","Probably — though I hold it with some nuance","Uncertain — I am still working this out","No — I think He gives wisdom and freedom more than a specific script"]},
    # 94
    {"category":"Life & Character","format":"QA","question":"How do you handle the feeling that you are not where you thought you would be by now?"},
    # 95
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to be someone who is truly easy to love — not just loveable?"},
    # 96
    {"category":"Christian Faith","format":"QA","question":"What does it mean to love your enemy — and have you ever had to actually do it?"},
    # 97
    {"category":"Life & Character","format":"QA","question":"How do you approach a season when your sense of identity feels unclear or genuinely shaken?"},
    # 98
    {"category":"Dating & Marriage","format":"QA","question":"What is one red flag in early dating that you now take more seriously than you used to?"},
    # 99
    {"category":"Fun & Icebreakers","format":"QA","question":"What is a creative skill or interest you have that you wish you had more time to develop?"},
    # 100
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about compatibility — is it something you discover or something you build?"},
    # 101
    {"category":"Christian Faith","format":"QA","question":"What does it mean to experience God's joy even in suffering — not just despite it?"},
    # 102
    {"category":"Life & Character","format":"QA","question":"How do you handle a situation where you know you are right but pursuing it would cost you a meaningful relationship?"},
    # 103
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to you to be someone's safe person — the one they run toward, not away from?"},
    # 104
    {"category":"Culture & Society","format":"QA","question":"What does it mean to you that Christians are called to be salt and light in a world that is often indifferent or hostile?"},
    # 105
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about love that you have only been able to understand through experiencing a real loss?"},
    # 106
    {"category":"Christian Faith","format":"QA","question":"What does it mean to be filled with the Holy Spirit — and is that something you actively seek?"},
    # 107
    {"category":"Life & Character","format":"QA","question":"What does it look like to be genuinely at peace with your own story without being defined by it?"},
    # 108
    {"category":"Dating & Marriage","format":"QA","question":"How do you approach the idea of falling in love with someone's potential rather than with who they actually are now?"},
    # 109
    {"category":"Dating & Marriage","format":"QA","question":"What does it look like for two people to sharpen each other without tearing each other down?"},
    # 110 — POLL (Stream C)
    {"category":"Dating & Marriage","format":"Poll","question":"Would you walk away from a relationship that looked right on paper but felt persistently unsettled inside?","options":["Yes — an unexplained lack of peace matters deeply to me","Probably — I would take the unrest very seriously","Maybe — I would want more clarity and time first","No — feelings can be wrong; I would push through it"]},
    # 111 — POLL (Stream D)
    {"category":"Life & Character","format":"Poll","question":"Do you think you are generally self-aware — able to see yourself clearly, including your blind spots?","options":["Yes — self-awareness is something I actively cultivate","Mostly — though I know I have gaps","Somewhat — I see some things clearly and miss others","Not really — it is something I want to grow in"]},
    # 112
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing you need in a partner that never shows up on anyone's formal list of requirements?"},
    # 113
    {"category":"Christian Faith","format":"QA","question":"What does it mean to delight yourself in the Lord in a real, practical, everyday way?"},
    # 114
    {"category":"Life & Character","format":"QA","question":"What does it mean to live a life that is both intentional and surrendered — without one canceling the other?"},
    # 115
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing you need to give yourself permission to actually want in a relationship?"},
    # 116
    {"category":"Fun & Icebreakers","format":"QA","question":"What is one place you have visited — or would most want to visit — that has or would shape how you see the world?"},
    # 117
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to choose a partner for who they are becoming, not just who they are today?"},
    # 118
    {"category":"Christian Faith","format":"QA","question":"How do you cultivate spiritual hunger when you are in a season of comfort and plenty?"},
    # 119
    {"category":"Life & Character","format":"QA","question":"What is one area where being patient with yourself has made you more patient with others?"},
    # 120
    {"category":"Family & Children","format":"QA","question":"What does it mean to raise children in a way that makes faith attractive rather than obligatory?"},
    # 121
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about the role of friendship in a marriage — is marrying your best friend a cliche or a real goal?"},
    # 122
    {"category":"Christian Faith","format":"QA","question":"What does the fruit of the Spirit look like in your relationships — not just in private but in how others actually experience you?"},
    # 123
    {"category":"Life & Character","format":"QA","question":"What does it mean to live with integrity when integrity costs you something concrete?"},
    # 124
    {"category":"Dating & Marriage","format":"QA","question":"What is one area of growth you would want to see in yourself before entering your next serious relationship?"},
    # 125
    {"category":"Fun & Icebreakers","format":"QA","question":"What is a moment when you felt unexpectedly proud of someone — and what did it reveal about your own values?"},
    # 126
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about the difference between a soulmate and a covenant partner?"},
    # 127
    {"category":"Dating & Marriage","format":"QA","question":"What is one way that loving someone has required you to confront your own selfishness?"},
    # 128 — POLL (Stream C)
    {"category":"Dating & Marriage","format":"Poll","question":"Do you think having a detailed list of qualities for a future spouse is helpful, or does it do more harm than good?","options":["Helpful — clarity protects you from settling","Probably helpful — as long as it stays flexible","Probably harmful — lists can blind you to real people","Harmful — love does not come from a checklist"]},
    # 129 — POLL (Stream D)
    {"category":"Christian Faith","format":"Poll","question":"Is regular fasting a spiritual discipline you currently practice or genuinely intend to practice?","options":["Yes — it is a consistent part of my faith life","Occasionally — it is something I return to","I want to but have not been consistent","No — it is not something I currently practice"]},
    # 130
    {"category":"Life & Character","format":"QA","question":"What does it look like to hold your future loosely without becoming passive about it?"},
    # 131
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about physical affection in dating — as bonding, as a boundary challenge, or as both?"},
    # 132
    {"category":"Christian Faith","format":"QA","question":"How do you think about the authority of Scripture in a world that increasingly challenges its assumptions?"},
    # 133
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing you would do differently in how you have pursued or received love in the past?"},
    # 134
    {"category":"Fun & Icebreakers","format":"QA","question":"What is one piece of art, music, or literature that has genuinely moved you, and why?"},
    # 135
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about the relationship between romantic love and commitment — which comes first, and which sustains?"},
    # 136
    {"category":"Christian Faith","format":"QA","question":"What does it mean to fear God rather than people — and have you ever had to choose between the two?"},
    # 137
    {"category":"Life & Character","format":"QA","question":"What does it mean to take full responsibility for your life — not your circumstances, but your response to them?"},
    # 138
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about the way you receive love that you genuinely wish you were better at?"},
    # 139
    {"category":"Culture & Society","format":"QA","question":"How do you think about the relationship between faith and science — do you find them in conflict?"},
    # 140
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to marry someone's whole life — not just the parts you see during dating?"},
    # 141
    {"category":"Christian Faith","format":"QA","question":"What does it look like to trust God with a desire that has been genuinely unfulfilled for a long time?"},
    # 142
    {"category":"Life & Character","format":"QA","question":"How do you deal with the gap between who you want to be and who you actually are on hard days?"},
    # 143
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about your communication style that you know would require patience from a future partner?"},
    # 144
    {"category":"Family & Children","format":"QA","question":"What does it look like to pass on a living, breathing faith to your children rather than just religious habits?"},
    # 145
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean for a couple to have a shared vision for life — and how important is it in choosing a partner?"},
    # 146 — POLL (Stream C)
    {"category":"Dating & Marriage","format":"Poll","question":"Do you believe God has one specific person intended for you to marry, or do you think that framework is more complicated than that?","options":["Yes — I believe God has a specific person for me","Leaning yes — though I hold it with some nuance","Uncertain — I genuinely wrestle with this question","No — I think it is more about two people choosing well"]},
    # 147 — POLL (Stream D)
    {"category":"Dating & Marriage","format":"Poll","question":"Do you think men and women should approach initiating romantic interest differently today than in previous generations?","options":["Yes — I think distinct approaches still make sense","Somewhat — the principles remain but the form can adapt","Probably not — I think it should be mostly the same","No — the old distinctions do not apply today"]},
    # 148
    {"category":"Christian Faith","format":"QA","question":"What does it mean that God sees you completely and loves you fully — and do you live as though you actually believe that?"},
    # 149
    {"category":"Life & Character","format":"QA","question":"What is one area of your life where you are still waiting for your courage to match your conviction?"},
    # 150
    {"category":"Dating & Marriage","format":"QA","question":"What does emotional safety in a marriage look like when both partners are exhausted and depleted?"},
    # 151
    {"category":"Fun & Icebreakers","format":"QA","question":"What is something you do to stay genuinely hopeful when the world feels heavy?"},
    # 152
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about the concept of romantic jealousy — is it a healthy signal or a dangerous pattern?"},
    # 153
    {"category":"Christian Faith","format":"QA","question":"What does it mean to abide in Christ when your circumstances are genuinely difficult?"},
    # 154
    {"category":"Life & Character","format":"QA","question":"What do you do when you know you are wrong but pride makes it hard to admit it?"},
    # 155
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to build a relationship on covenant rather than on contract?"},
    # 156
    {"category":"Culture & Society","format":"QA","question":"How do you think about Christians' responsibility to care for creation as part of their faith?"},
    # 157
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about the way you love that you would want someone to see clearly before they commit to you?"},
    # 158
    {"category":"Christian Faith","format":"QA","question":"What does it mean to seek first the kingdom of God in the specific decisions you are facing right now?"},
    # 159
    {"category":"Life & Character","format":"QA","question":"What is one way you have been surprised by your own capacity for grace toward someone who genuinely hurt you?"},
    # 160
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about the concept of sacrifice in love — not the romantic kind, but the daily invisible kind?"},
    # 161
    {"category":"Fun & Icebreakers","format":"QA","question":"What is something that has shifted your understanding of what it means to be truly generous?"},
    # 162
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing you believe about love that most people in your generation seem to disagree with?"},
    # 163
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to be known deeply and to choose to stay — what makes that kind of love possible?"},
    # 164 — POLL (Stream C)
    {"category":"Dating & Marriage","format":"Poll","question":"Is it fair to enter a serious relationship when you know you are still in a critical season of personal growth?","options":["Yes — growth never fully stops; do not wait forever","Probably — it depends on what is being worked through","Uncertain — it really depends on the type of growth","No — some stability should come before you bring someone else in"]},
    # 165
    {"category":"Life & Character","format":"QA","question":"How do you stay present in a season that is mostly ordinary and unglamorous?"},
    # 166
    {"category":"Dating & Marriage","format":"QA","question":"What is one quality you have been quietly looking for in a partner that you rarely actually admit to?"},
    # 167 — POLL (Stream D)
    {"category":"Christian Faith","format":"Poll","question":"Do you find that corporate worship on Sundays genuinely impacts how you live the rest of your week?","options":["Yes — Sunday shapes my entire week","Mostly — when I am truly engaged it does","Sometimes — it is inconsistent for me","Not really — I struggle to carry it past Sunday"]},
    # 168
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about a partner's close friendship with their ex — where is the line?"},
    # 169
    {"category":"Family & Children","format":"QA","question":"How do you think about having open, honest conversations with your children about your own past mistakes?"},
    # 170
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to be genuinely content with where you are while still pursuing something more?"},
    # 171
    {"category":"Christian Faith","format":"QA","question":"What does it look like to pursue God when you are genuinely angry at Him?"},
    # 172
    {"category":"Life & Character","format":"QA","question":"What is one thing you have learned about yourself by watching how you treat people who can do nothing for you?"},
    # 173
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about love languages when they conflict — when you naturally give one way but need to receive another?"},
    # 174
    {"category":"Culture & Society","format":"QA","question":"How do you think about the tension between personal holiness and engaging authentically with a broken world?"},
    # 175
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to love someone well in their worst season — not just their best?"},
    # 176
    {"category":"Fun & Icebreakers","format":"QA","question":"What is one thing that happened in your childhood that you believe has shaped almost everything since?"},
    # 177
    {"category":"Dating & Marriage","format":"QA","question":"How do you handle feeling vulnerable in a relationship when vulnerability has genuinely hurt you before?"},
    # 178
    {"category":"Christian Faith","format":"QA","question":"What does it mean to live with eternity in view — and does that reality actually change your daily decisions?"},
    # 179
    {"category":"Life & Character","format":"QA","question":"What is one thing about your generation's approach to relationships that you think is genuinely wise?"},
    # 180
    {"category":"Dating & Marriage","format":"QA","question":"What is one aspect of God's love that most directly changes how you show up in your relationships?"},
    # 181
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about dating someone who is very different from you in temperament or personality?"},
    # 182
    {"category":"Christian Faith","format":"QA","question":"What does it mean to be a disciple — not just a believer — and what is the practical difference?"},
    # 183
    {"category":"Life & Character","format":"QA","question":"How do you stay connected to your values when the environment around you is constantly pulling you in another direction?"},
    # 184 — POLL (Stream C)
    {"category":"Dating & Marriage","format":"Poll","question":"Do you think it is healthy for a couple to spend most of their free time together, or is regular independent space important?","options":["Independent space is very important — I would want that","Leaning toward space — though togetherness matters too","Leaning toward togetherness — I want to share most of life","As much togetherness as possible — that is the goal"]},
    # 185 — POLL (Stream D)
    {"category":"Life & Character","format":"Poll","question":"Would you say your sense of worth is more rooted in what you do and achieve, or in who you are before God?","options":["Rooted in who I am before God — genuinely","Mostly in God — though performance still tugs at me","Honestly more in what I do — it is something I am working on","Mostly in what I achieve — I know I need to change this"]},
    # 186
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about your own needs in a relationship that you have been hesitant to honestly ask for?"},
    # 187
    {"category":"Family & Children","format":"QA","question":"How do you think about the role of faith in a child's development — is it something you teach or something they catch?"},
    # 188
    {"category":"Dating & Marriage","format":"QA","question":"What do you think is the most important thing a couple can do to protect their relationship over decades?"},
    # 189
    {"category":"Christian Faith","format":"QA","question":"What does it mean to hope in God when your circumstances give you no visible reason to hope?"},
    # 190
    {"category":"Life & Character","format":"QA","question":"What is one thing about your upbringing that you are genuinely grateful for — not in spite of the hard parts but because of them?"},
    # 191
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to be a partner who is genuinely safe to fail around?"},
    # 192
    {"category":"Fun & Icebreakers","format":"QA","question":"What is one thing you think most people miss about what makes a community truly healthy?"},
    # 193
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about the concept of growing apart versus growing together — what is the real cause?"},
    # 194
    {"category":"Christian Faith","format":"QA","question":"What does it mean to be a living witness — not just a speaking one?"},
    # 195
    {"category":"Life & Character","format":"QA","question":"What is one area where you have had to choose faithfulness over feelings for an extended period of time?"},
    # 196
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to have a shared vision for life with a partner — how necessary is it, and what happens without it?"},
    # 197
    {"category":"Culture & Society","format":"QA","question":"What does it mean for the church to be prophetic in today's culture — not political, but genuinely prophetic?"},
    # 198
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about how you handle money right now that you know would directly affect a future relationship?"},
    # 199
    {"category":"Family & Children","format":"QA","question":"How do you think about the concept of family loyalty — how much should it bind you, and when does it stop?"},
    # 200
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to be a partner who leads with service rather than with authority?"},
    # 201
    {"category":"Christian Faith","format":"QA","question":"What does it mean that God is patient with you — and how does that actually shape your patience toward yourself?"},
    # 202
    {"category":"Life & Character","format":"QA","question":"What is one way you have had to let go of control in order to grow in genuine trust?"},
    # 203
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about your own argument or conflict style that you know needs to change?"},
    # 204 — POLL (Stream C)
    {"category":"Dating & Marriage","format":"Poll","question":"Do you think couples should set explicit physical boundaries early in a dating relationship, or let them develop more naturally?","options":["Yes — clear and early is the wisest approach","Probably — sooner rather than later is better","Somewhat — I think it varies by the relationship","No — I think they should develop organically over time"]},
    # 205 — POLL (Stream D)
    {"category":"Christian Faith","format":"Poll","question":"Is memorizing Scripture a regular spiritual practice for you?","options":["Yes — it is a consistent discipline in my life","Occasionally — I do it in certain seasons","I want to but have not been consistent","No — it is not something I currently practice"]},
    # 206
    {"category":"Family & Children","format":"QA","question":"How do you think about the role of faith in a child's development — is it something you teach intentionally or something they absorb?"},
    # 207
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about supporting a partner's dreams when they compete with your own sense of direction?"},
    # 208
    {"category":"Christian Faith","format":"QA","question":"What does it mean to be transformed by the renewing of your mind — what does that process actually look like?"},
    # 209
    {"category":"Life & Character","format":"QA","question":"How do you handle situations where what is good and what is easy are completely opposite things?"},
    # 210
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about how men and women relate that you think the church needs to talk about more honestly?"},
    # 211
    {"category":"Culture & Society","format":"QA","question":"How do you think Christians should engage with the entertainment and media they consume?"},
    # 212
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to be loved not for what you do but for who you are — and do you actually believe that is possible?"},
    # 213
    {"category":"Family & Children","format":"QA","question":"What does it look like to navigate in-law relationships with wisdom and grace when there are real tensions?"},
    # 214
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing you are learning about yourself in this current season of waiting?"},
    # 215
    {"category":"Christian Faith","format":"QA","question":"How do you think about the role of community in keeping you accountable to your actual convictions?"},
    # 216
    {"category":"Life & Character","format":"QA","question":"What is one way you have seen the connection between faithfulness in small things and trustworthiness in larger ones?"},
    # 217
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about the concept of romantic love as a choice versus a feeling — where do you actually land?"},
    # 218
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing that would make you genuinely reconsider pursuing someone you were already interested in?"},
    # 219
    {"category":"Christian Faith","format":"QA","question":"What does it look like to press into God during a season when faith feels like effort rather than grace?"},
    # 220
    {"category":"Life & Character","format":"QA","question":"How do you handle feeling stuck — in a season, in a habit, or in a relationship?"},
    # 221
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to pray for your future spouse right now — what do you actually pray?"},
    # 222 — POLL (Stream C)
    {"category":"Dating & Marriage","format":"Poll","question":"Would you be open to pursuing premarital counseling even if your relationship already felt healthy and strong?","options":["Yes — healthy couples benefit most from it","Probably — I would be open to it","Maybe — I would consider it depending on the situation","Probably not — if things are healthy, I would not feel the need"]},
    # 223 — POLL (Stream D)
    {"category":"Life & Character","format":"Poll","question":"Do you think you are genuinely good at receiving criticism and feedback from people who care about you?","options":["Yes — I actively welcome it","Mostly — though it takes me a moment to receive it well","Somewhat — it depends heavily on who is giving it","No — I struggle with criticism even when it is loving"]},
    # 224
    {"category":"Dating & Marriage","format":"QA","question":"What is one long-term quality in a relationship that most people undervalue when they are young?"},
    # 225
    {"category":"Christian Faith","format":"QA","question":"What does it mean to give cheerfully and sacrificially — and does your actual giving reflect that?"},
    # 226
    {"category":"Life & Character","format":"QA","question":"What is one part of your character that has been hardest won — paid for by real cost?"},
    # 227
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to be generous in a relationship — not financially, but emotionally and with your time?"},
    # 228
    {"category":"Fun & Icebreakers","format":"QA","question":"What is one conversation you have had that genuinely changed how you see something important?"},
    # 229
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean for two people to grow in the same direction — and what happens when they do not?"},
    # 230
    {"category":"Christian Faith","format":"QA","question":"What does it mean to be a peacemaker in a relationship — not a conflict-avoiding peacekeeper?"},
    # 231
    {"category":"Life & Character","format":"QA","question":"What is one way you have grown in your ability to receive love that you are still learning?"},
    # 232
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about being truly known and not running away that you think is central to any real relationship?"},
    # 233
    {"category":"Family & Children","format":"QA","question":"What does it mean to you for your home to be a place of genuine hospitality — not just a nice house?"},
    # 234
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about the role of past trauma in shaping what you genuinely need from a partner?"},
    # 235
    {"category":"Christian Faith","format":"QA","question":"What does it mean to take up your cross daily — not just in theory, but in the actual decisions of this season?"},
    # 236
    {"category":"Life & Character","format":"QA","question":"How do you stay motivated in the middle of a long, slow obedience that no one is watching?"},
    # 237
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about the concept of romantic love that you have had to let go of as you have matured?"},
    # 238
    {"category":"Fun & Icebreakers","format":"QA","question":"What is one thing you believe is genuinely undervalued in today's culture that you think matters deeply?"},
    # 239
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about the concept of being spiritually ready versus spiritually perfect — is there a real distinction?"},
    # 240 — POLL (Stream C)
    {"category":"Dating & Marriage","format":"Poll","question":"Do you think it is important for a couple to have a shared sense of mission or calling beyond their own household?","options":["Yes — a shared mission is essential","Probably — I think it adds something vital","Nice to have — but not strictly necessary","No — building a healthy home is mission enough"]},
    # 241 — POLL (Stream D)
    {"category":"Christian Faith","format":"Poll","question":"Do you feel that your local church community genuinely knows and actively supports you?","options":["Yes — I feel truly known and supported","Mostly — though there is room to go deeper","Somewhat — I know some people well but not many","No — I feel largely unknown in my church community"]},
    # 242
    {"category":"Christian Faith","format":"QA","question":"What does it mean to walk humbly before God — not just morally, but in how you hold your own opinions?"},
    # 243
    {"category":"Life & Character","format":"QA","question":"What is one way you have had to let go of control to grow in genuine trust?"},
    # 244
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about your communication style that most surprises people who know you well?"},
    # 245
    {"category":"Fun & Icebreakers","format":"QA","question":"What is one thing about your faith that makes you genuinely different in a way you are actually proud of?"},
    # 246
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about long-term love that you have come to believe requires more courage than people expect?"},
    # 247
    {"category":"Christian Faith","format":"QA","question":"What does it mean to be in the world but not of it in the specific context of your daily life?"},
    # 248
    {"category":"Life & Character","format":"QA","question":"What is one way you have learned to love yourself that has made you genuinely better at loving others?"},
    # 249
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about a partner's relationship with God being private versus something they share openly with you?"},
    # 250
    {"category":"Family & Children","format":"QA","question":"How do you think about the relationship between your own healing and your capacity to parent well?"},
    # 251
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about the institution of marriage that you believe modern culture has profoundly misunderstood?"},
    # 252
    {"category":"Christian Faith","format":"QA","question":"What does genuine repentance feel like — not just saying sorry, but actually turning?"},
    # 253
    {"category":"Life & Character","format":"QA","question":"What is one value you hold that you have had to defend at some genuine personal cost?"},
    # 254
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about the relationship between vulnerability and strength — can they genuinely coexist in a relationship?"},
    # 255
    {"category":"Culture & Society","format":"QA","question":"How do you think about the Christian's role in caring for the next generation — beyond just your own children?"},
    # 256
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing you believe about the purpose of marriage that has changed what you are actually looking for?"},
    # 257
    {"category":"Christian Faith","format":"QA","question":"What does it mean to you to be part of the body of Christ — not just to attend a church, but to genuinely be the church?"},
    # 258
    {"category":"Life & Character","format":"QA","question":"What is one thing you do to guard your own peace when the people around you are not at peace?"},
    # 259
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about how you parent yourself that you would want a partner to understand?"},
    # 260 — POLL (Stream C)
    {"category":"Dating & Marriage","format":"Poll","question":"Do you think it is wise to share your full romantic history with a future spouse, or is some level of privacy appropriate?","options":["Full transparency — a future spouse deserves to know everything","Mostly open — what is relevant and shaping, I would share","Selective — the past is the past and some things are private","Privacy is appropriate — no one needs to know everything"]},
    # 261 — POLL (Stream D)
    {"category":"Dating & Marriage","format":"Poll","question":"Is financial compatibility — similar values, habits, and goals around money — a dealbreaker for you?","options":["Yes — financial alignment is essential to me","Probably — significant misalignment would be a serious concern","Maybe — it depends on how different we actually are","No — money is not something I would end a relationship over"]},
    # 262
    {"category":"Christian Faith","format":"QA","question":"What does it look like to serve someone sacrificially when you get nothing in return and no one sees it?"},
    # 263
    {"category":"Life & Character","format":"QA","question":"What is one way your faith has genuinely protected you from a decision that would have caused you serious harm?"},
    # 264
    {"category":"Dating & Marriage","format":"QA","question":"How do you handle the fear of being hurt again when you have already been hurt in love before?"},
    # 265
    {"category":"Fun & Icebreakers","format":"QA","question":"What is one way that failure has been more formative for you than success?"},
    # 266
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to give someone the benefit of the doubt — and when does that become something closer to naivety?"},
    # 267
    {"category":"Christian Faith","format":"QA","question":"What does it mean to you that God keeps His promises even when circumstances make it look completely impossible?"},
    # 268
    {"category":"Life & Character","format":"QA","question":"What is one area where you have had to choose discipline over comfort for an extended period — and what did it build in you?"},
    # 269
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about the tension between high standards and genuine acceptance in how you pursue a relationship?"},
    # 270
    {"category":"Family & Children","format":"QA","question":"What does it look like for parents to model repentance and forgiveness openly in front of their children?"},
    # 271
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to stay emotionally faithful — not just physically — before marriage?"},
    # 272
    {"category":"Christian Faith","format":"QA","question":"How do you think about what it means to truly love the local church — its actual people, not just the idea of it?"},
    # 273
    {"category":"Life & Character","format":"QA","question":"What is one thing about how you relate to people that you know has grown significantly in the last few years?"},
    # 274
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about the role of service in a marriage — not just to each other but together for others?"},
    # 275
    {"category":"Fun & Icebreakers","format":"QA","question":"What is one thing you wish you could tell someone who is in the hardest season of their life right now?"},
    # 276
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to you that your marriage could be a witness to others — not just a personal blessing?"},
    # 277
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about pursuing someone that you think gets overlooked in favor of just letting things happen naturally?"},
    # 278 — POLL (Stream C)
    {"category":"Dating & Marriage","format":"Poll","question":"Is it important to you that your future spouse has a meaningful relationship with your close friends?","options":["Yes — their fitting into my community matters deeply","Probably — I would want my people to genuinely like them","Somewhat — it would matter but not be a dealbreaker","Not really — friendships and a relationship can stay separate"]},
    # 279 — POLL (Stream D)
    {"category":"Life & Character","format":"Poll","question":"Would you say you are currently living in a way that reflects your deepest values, or is there a significant gap?","options":["Yes — my life and values are largely aligned","Mostly — with some areas I am actively working on","There is a noticeable gap — I am aware and working on it","There is a significant gap — this is something I need to address"]},
    # 280
    {"category":"Christian Faith","format":"QA","question":"What does it mean to forgive as the Lord has forgiven you — completely and at genuine cost?"},
    # 281
    {"category":"Life & Character","format":"QA","question":"What is one thing you believe about yourself that has been hard to hold onto against voices that say otherwise?"},
    # 282
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to be spiritually present for someone you are in a relationship with — not controlling, but praying and protecting?"},
    # 283
    {"category":"Fun & Icebreakers","format":"QA","question":"What is one book, sermon, or piece of teaching that genuinely shifted how you see yourself or the world?"},
    # 284
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about the risk of loving someone deeply when there is no guarantee of the outcome?"},
    # 285
    {"category":"Christian Faith","format":"QA","question":"What does it mean to offer yourself fully to God for whatever He wants to do through you in a given season?"},
    # 286
    {"category":"Life & Character","format":"QA","question":"How do you think about the relationship between contentment and ambition — are they in real tension?"},
    # 287
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about how you express love that most people would never see unless they were genuinely close to you?"},
    # 288
    {"category":"Family & Children","format":"QA","question":"What does it mean to let your children see you struggle with your faith in a healthy, honest way?"},
    # 289
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to be a partner who stays present when your instinct is to pull away?"},
    # 290
    {"category":"Christian Faith","format":"QA","question":"How do you think about the promise of heaven — does it genuinely shape your present, or is it mostly a comfort for later?"},
    # 291
    {"category":"Life & Character","format":"QA","question":"What is one thing you need to stop apologizing for because it is actually part of who God made you to be?"},
    # 292
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to build a life together rather than just sharing a life — what is the actual difference?"},
    # 293
    {"category":"Fun & Icebreakers","format":"QA","question":"What is one question you have never been asked that you would actually love to be asked?"},
    # 294
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about emotional unavailability — have you experienced it in yourself or in someone you loved?"},
    # 295
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to you that the church describes marriage as a picture of Christ and the church — not just a relationship?"},
    # 296 — POLL (Stream C)
    {"category":"Dating & Marriage","format":"Poll","question":"Do you think couples should intentionally plan time apart in marriage to protect individual identity and personal growth?","options":["Yes — I think it is essential for a healthy marriage","Probably — regular independent space is wise","Somewhat — some is healthy but I would not plan much of it","No — I want to share almost all of life with my spouse"]},
    # 297 — POLL (Stream D)
    {"category":"Christian Faith","format":"Poll","question":"Do you believe that fasting meaningfully deepens your connection with God?","options":["Yes — I have experienced this directly","Probably — I believe it does even when I do not feel it","Uncertain — I am still forming a view on this","I am not sure — I have not practiced it enough to know"]},
    # 298
    {"category":"Christian Faith","format":"QA","question":"What does it mean to be faithful in waiting rather than just enduring it?"},
    # 299
    {"category":"Life & Character","format":"QA","question":"What is one thing about your relationship with time — deadlines, spontaneity, pace — that a partner would genuinely need to know?"},
    # 300
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing you would most want to protect in your marriage when life gets busy and starts to crowd everything out?"},
    # 301
    {"category":"Fun & Icebreakers","format":"QA","question":"What is one thing you have outgrown that was once very important to you — and how do you feel about that now?"},
    # 302
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about the idea that love is not just something you feel, but something you practice — every single day?"},
    # 303
    {"category":"Christian Faith","format":"QA","question":"What does it mean to be set free by the truth — and is there a truth you are still working toward fully accepting?"},
    # 304
    {"category":"Life & Character","format":"QA","question":"What is one area of your character where you know there is a gap between how you present yourself and who you actually are?"},
    # 305
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing you believe every person should know about themselves before seriously pursuing marriage?"},
    # 306
    {"category":"Culture & Society","format":"QA","question":"How do you think about Christians engaging in local government, education, or community leadership — is that a calling?"},
    # 307
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to you for a couple to have a shared prayer life — beyond just praying before meals?"},
    # 308
    {"category":"Christian Faith","format":"QA","question":"What does it mean to have a faith that is truly your own — not inherited, not cultural, but genuinely personal?"},
    # 309
    {"category":"Life & Character","format":"QA","question":"What is one thing about your personality that you have had to learn to steward rather than suppress?"},
    # 310
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about the idea of falling out of love — can it happen to people who are genuinely committed?"},
    # 311
    {"category":"Fun & Icebreakers","format":"QA","question":"What is one thing you have done purely for joy — no goal, no productivity, no audience — that you would do again?"},
    # 312
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to build a friendship with someone before romance — is that the right order?"},
    # 313
    {"category":"Christian Faith","format":"QA","question":"What does it look like to encounter God in the ordinary moments of your life — not just in church or in crisis?"},
    # 314
    {"category":"Life & Character","format":"QA","question":"What is one thing you have noticed about yourself in community that you do not see when you are alone?"},
    # 315
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about your relationship with your father or mother that has shaped what you look for in a partner?"},
    # 316 — POLL (Stream C)
    {"category":"Dating & Marriage","format":"Poll","question":"Do you think a couple should live in the same city for at least a year before getting engaged?","options":["Yes — shared daily life reveals things dating cannot","Probably — proximity over time matters a lot","Maybe — it depends on the relationship and circumstances","No — the length of engagement can compensate for distance"]},
    # 317 — POLL (Stream D)
    {"category":"Dating & Marriage","format":"Poll","question":"Is it important to you that your future spouse has a sense of humor that can match or complement yours?","options":["Yes — shared humor is non-negotiable for me","Probably — it matters more than people admit","Somewhat — it is nice but not essential","No — humor is not something I would weigh heavily"]},
    # 318
    {"category":"Christian Faith","format":"QA","question":"What does it mean to be honest before God — not just obedient, but genuinely transparent with Him?"},
    # 319
    {"category":"Life & Character","format":"QA","question":"What is one thing that consistently reveals your character under pressure that you are still working on?"},
    # 320
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to you to be loved with patience — and have you actually received that kind of love?"},
    # 321
    {"category":"Fun & Icebreakers","format":"QA","question":"What is something you have changed your mind about in the last five years that would surprise the version of you from then?"},
    # 322
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about the idea of a partner who challenges you versus one who supports you — do you need both?"},
    # 323
    {"category":"Christian Faith","format":"QA","question":"What does it mean to be salt and light in your workplace, neighborhood, or social world without being preachy?"},
    # 324
    {"category":"Life & Character","format":"QA","question":"What is one thing you have learned about forgiveness that you could not have learned without being genuinely and significantly wronged?"},
    # 325
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to love someone freely — without fear of rejection or the constant need for their approval?"},
    # 326
    {"category":"Family & Children","format":"QA","question":"How do you think about creating a family culture where every member feels genuinely seen and valued?"},
    # 327
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about the concept of finding your person that you think sets people up for disappointment?"},
    # 328
    {"category":"Christian Faith","format":"QA","question":"What does it mean to endure hardship as formation — not as punishment, but as the shaping of your character?"},
    # 329
    {"category":"Life & Character","format":"QA","question":"What is one area where you know your words and your actual actions are not yet in alignment?"},
    # 330
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to be genuinely curious about someone — not projecting onto them, but actually wanting to understand who they are?"},
    # 331
    {"category":"Fun & Icebreakers","format":"QA","question":"What is one way that a person you barely knew changed how you think about something that matters to you?"},
    # 332
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to be patient in love — not passive, but genuinely unhurried?"},
    # 333
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about dating in the digital age that you find genuinely difficult to navigate with integrity?"},
    # 334 — POLL (Stream C)
    {"category":"Dating & Marriage","format":"Poll","question":"Do you think the way a person treats service workers or strangers reveals something significant about their character?","options":["Yes — it is one of the most honest tests of character","Probably — it reveals more than most people realize","Somewhat — it is one signal among many","Not really — context matters too much to read into it"]},
    # 335 — POLL (Stream D)
    {"category":"Life & Character","format":"Poll","question":"Do you think you are better at giving love to others than you are at receiving it?","options":["Yes — giving comes far more naturally to me","Probably — receiving is harder than most people know","About the same — I do not think one is easier for me","No — I actually find receiving easier than giving"]},
    # 336
    {"category":"Christian Faith","format":"QA","question":"What does it look like to bring your whole self to God — not just the presentable, put-together parts?"},
    # 337
    {"category":"Life & Character","format":"QA","question":"What is one way you have learned to set a boundary that protects your peace without damaging a relationship?"},
    # 338
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about the concept of falling in love versus building in love — which do you think is more sustainable?"},
    # 339
    {"category":"Fun & Icebreakers","format":"QA","question":"What is one thing about your personality that people almost always misread until they know you well?"},
    # 340
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to pray for someone you are interested in before you tell them?"},
    # 341
    {"category":"Christian Faith","format":"QA","question":"What does it mean for your faith to be costly — is there something it has genuinely cost you?"},
    # 342
    {"category":"Life & Character","format":"QA","question":"What is one area of your life where you are learning to rest without feeling guilty about it?"},
    # 343
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about the concept of spiritual intimacy in a marriage — is it different from emotional or physical intimacy?"},
    # 344
    {"category":"Family & Children","format":"QA","question":"What does it mean to be honest with your children about your own struggles and history in a way that builds trust rather than fear?"},
    # 345
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing you believe about love that you have had to defend against a culture that fundamentally disagrees?"},
    # 346
    {"category":"Christian Faith","format":"QA","question":"What does it mean to you that God is close to the brokenhearted — have you actually experienced that?"},
    # 347
    {"category":"Life & Character","format":"QA","question":"What is one thing about your relationship with yourself that you would want your future spouse to understand early on?"},
    # 348
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about the difference between a partner who accepts you as you are and one who sees who you can become?"},
    # 349
    {"category":"Fun & Icebreakers","format":"QA","question":"What is one thing you do that most people around you do not do, that you think is quietly important?"},
    # 350
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to be someone's person — the one they call when everything is going wrong?"},
    # 351
    {"category":"Christian Faith","format":"QA","question":"What does it look like to be led by the Spirit in a specific relationship or major decision — not just in theory?"},
    # 352
    {"category":"Life & Character","format":"QA","question":"What is one thing you have noticed about how you treat people when you are tired or under stress that you wish were different?"},
    # 353
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about your personality that you think would be most surprising to a future spouse once they really see it?"},
    # 354
    {"category":"Culture & Society","format":"QA","question":"How do you think about Christians being genuinely different from culture without becoming isolated from it?"},
    # 355
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to build a marriage that survives not just the hard seasons but the long, ordinary ones?"},
    # 356
    {"category":"Christian Faith","format":"QA","question":"What does it mean to live with a deep awareness of God's presence — not just in church but throughout your actual day?"},
    # 357
    {"category":"Life & Character","format":"QA","question":"What is one thing you have learned about yourself by paying attention to what consistently moves you or breaks your heart?"},
    # 358
    {"category":"Dating & Marriage","format":"QA","question":"How do you think about fighting fair in a marriage — what are your non-negotiables when conflict arises?"},
    # 359
    {"category":"Fun & Icebreakers","format":"QA","question":"What is one thing you have learned about love that could only be understood by watching it lived out in someone else?"},
    # 360
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to you to give someone your whole heart — knowing it might not be returned?"},
    # 361
    {"category":"Christian Faith","format":"QA","question":"What does it mean to be rooted and grounded in love — not just as doctrine, but as something you have lived?"},
    # 362
    {"category":"Life & Character","format":"QA","question":"What is one way that your relationship with God has made you a noticeably better friend, coworker, or family member?"},
    # 363
    {"category":"Dating & Marriage","format":"QA","question":"What is one thing about the way you see a future spouse that you hope is still completely true 20 years into marriage?"},
    # 364
    {"category":"Dating & Marriage","format":"QA","question":"What does it mean to choose someone on the worst day of your life — and is that a standard worth holding for a future spouse?"},
    # 365
    {"category":"Christian Faith","format":"QA","question":"What is one thing about God's character that you have had to learn through loss rather than abundance?"},
]

assert len(new_questions) == 366, f"Expected 366, got {len(new_questions)}"

# Verify poll count and positions
poll_positions = [i for i, q in enumerate(new_questions) if q["format"] == "Poll"]
expected_polls = sorted([18,19,36,37,54,55,70,75,92,93,110,111,128,129,146,147,
                          164,167,184,185,204,205,222,223,240,241,260,261,278,279,
                          296,297,316,317,334,335])
assert poll_positions == expected_polls, f"Poll mismatch!\nGot:      {poll_positions}\nExpected: {expected_polls}"
print(f"Poll positions verified: {poll_positions}")

# Load and append
with open("assets/lets_chat_questions.json", "r", encoding="utf-8") as f:
    existing = json.load(f)

assert len(existing) == 366, f"Expected 366 existing questions, got {len(existing)}"

combined = existing + new_questions

with open("assets/lets_chat_questions.json", "w", encoding="utf-8") as f:
    json.dump(combined, f, indent=2, ensure_ascii=False)

print(f"Done. Total questions: {len(combined)}")
