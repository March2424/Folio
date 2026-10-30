package hk.hkucs.dontwannaforget;

import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一条记录(票根/书/声音/音乐/足迹/写信)。同时是 Room 实体。
 * 原始图片与音频不在此表,由 Media 表引用。
 */
@Entity(tableName = "records")
public class Record {
    public static final int TICKET = 0, BOOK = 1, VOICE = 2, MUSIC = 3, FOOTPRINT = 4, LETTER = 5;
    public static final String SAMPLE_PREFIX = "sample-";

    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();
    public int type;
    public String title = "";
    public String sub = "";       // 导演/作者/歌手/地点
    public String date = "";      // yyyy.MM.dd,按本地日历日期存储
    public String rating;         // 评分,可空
    public String note = "";      // 感想/摘录/歌词/声音故事(换行分隔)
    public int c1, c2;            // 封面渐变色(无图时的占位)
    /**
     * 附加字段:
     * 票根 = 影院、影厅、座位、版本、票价、时间;
     * 书 = fields[0] 为 "cover" 表示有封面,否则以 note(摘录)作封面;
     * 声音 = fields[0] 为时长(如 "00:42"), fields[1] 为来源描述(如 "现场录音" / "本地上传");
     * 足迹 = 每条画布序列化数据。
     */
    public String[] fields = new String[0];
    public long createdAt;
    public long updatedAt;
    public long deletedAt;        // 0 = 未删除;>0 = 在回收站

    /** Room 使用 */
    public Record() {}

    @Ignore
    public Record(int type, String title, String sub, String date, String rating,
                  String note, String c1, String c2, String... fields) {
        this.type = type;
        this.title = title;
        this.sub = sub;
        this.date = date;
        this.rating = rating;
        this.note = note;
        this.c1 = Color.parseColor(c1);
        this.c2 = Color.parseColor(c2);
        this.fields = fields;
        this.createdAt = this.updatedAt = System.currentTimeMillis();
    }

    public String month() {
        return date.substring(0, 7);
    }

    /** 仅供调试构建预览的示例数据;id 带 sample- 前缀,便于与真实记录区分并清除。 */
    public static List<Record> sample() {
        List<Record> l = new ArrayList<>();
        // 10月06日: 音乐(仿 IMG_3870.jpg 歌词卡片) + 足迹 + 声音 + 电影 + 写信 同一天组合
        l.add(new Record(MUSIC, "純真", "Mayday", "2026.10.06", null,
                "世界的純真此刻為你有迷惑\n我想我應該輕輕放開你的手\n我卻沒有力氣這麼做",
                "#5A6558", "#9DB89A",
                "{\"fid\":\"serif\",\"tw\":0,\"lsz\":22.0,\"bl\":1,\"th\":\"耳机里循环了一整晚的歌，把这一句留在卡片里。\",\"sc\":\"MUSIC #0106\"}"));
        l.add(new Record(MUSIC, "已經離我太遠", "鄭麗欣", "2026.10.04", null,
                "一切的 人和時 地或事 現在太遠\n互撞後 那個你 淪為輕煙\n遺落的灰塵\n出發前 擦擦雙肩 再踢到一邊",
                "#8E4F4B", "#E7A35A",
                "{\"fid\":\"serif\",\"tw\":0,\"lsz\":20.5,\"bl\":1,\"th\":\"晚归巴士上听见的旋律。\",\"sc\":\"MUSIC #0218\"}"));
        l.add(new Record(FOOTPRINT, "维港海傍", "香港 · 坚尼地城", "2026.10.06", null,
                "日落之前沿着海边走了一段。",
                "#5A7D8C", "#E7A35A",
                "17:40|海风吹过来的时候，船正慢慢驶出港口。",
                "18:15|天色从金黄变成暗紫，路灯一盏盏亮起来。"));
        l.add(new Record(VOICE, "坚尼地城的海浪与轮渡汽笛", "香港 · 坚尼地城海傍", "2026.10.06", null,
                "傍晚六点半，潮水拍在堤岸石阶上的声音，远处有一声很长的轮渡汽笛。\n生活中总有些声音是你不想忘记的。",
                "#4A6670", "#D9A464", "00:42", "现场录音 · 海傍环境音"));
        l.add(new Record(TICKET, "嘉年华", "文晏", "2026.10.06", "5.0",
                "為了自己好，我們總是要試著思考跟察知，\n看完很久都沒說話。紅色的高跟鞋和很小的人，海浪一直在拍打著岸邊。",
                "#2E5E8C", "#A3322B", "UME影城", "5号厅", "G排 12座", "2D", "¥48", "19:40",
                "{\"fm\":0,\"nl\":0,\"es\":4,\"pt\":0,\"tt\":0,\"nb\":false,\"tsz\":28,\"bsz\":11.5,\"sc\":\"ID #004234\"}"));
        l.add(new Record(TICKET, "親愛的男孩", "万玛才旦", "2026.10.02", "4.9",
                "最近答應他要努力改善陪他玩的時間，他開始發現生活裡那些細碎的裂縫。\n每天看著銀幕上的光影變化，感覺他一直在思考這件事。",
                "#2B3030", "#6B706E", "百老汇电影中心", "1号艺术厅", "F排 08座", "2D 原版", "¥60", "20:15",
                "{\"fm\":0,\"nl\":2,\"es\":1,\"pt\":1,\"tt\":1,\"nb\":false,\"tsz\":26,\"bsz\":11.0,\"sc\":\"ID #004234\"}"));
        l.add(new Record(LETTER, "秋雨与落叶", "自己", "2026.10.06", null,
                "Dear 自己,\n\nMy sorrow, when she's here with me, thinks these dark days of autumn rain are beautiful as days can be.\n\n“把秋天第一场雨的声音留在打字机里。”\n\n愿你在忙碌的日子里，依然记得今晚海边的风。",
                "#D9D2C5", "#F4EFE4", "0"));

        // 09月14日: 还原 letterexp1/2/3.jpg 的示例信件
        l.add(new Record(LETTER, "The umbrella you left behind", "Mira", "2026.09.14", null,
                "Dear Mira,\n\nYour yellow umbrella is still by my door. I was going to post it back, but yesterday it rained, and I thought you'd forgive me for borrowing it one more time.\n\nI went out for bread. That was the whole plan. At the corner, the usual bakery had a handwritten sign: “Back when the rain stops.” No time, no apology. I stood there for a minute, admiring the confidence.\n\nThen the woman from the flower stall waved me over. Her awning was collecting water in one enormous pocket. She handed me a broom and said, “You look tall enough.”\n\nI pushed the canvas up. A small lake landed on the pavement. We both jumped back, far too late.\n\nSo there I was: dry head, wet shoes, no bread. You would have taken a photograph.",
                "#D9D2C5", "#F4EFE4", "0"));

        // 09月24日: 音乐 + 电影 + 书 + 足迹
        l.add(new Record(MUSIC, "成日受傷的女人", "Serrini", "2026.09.24", null,
                "今天也有好好吃饭\n在摇晃的车厢里\n把耳机音量调大了一格",
                "#3E4A45", "#C9C2AE",
                "{\"fid\":\"sketch\",\"tw\":1,\"lsz\":21.5,\"bl\":2,\"th\":\"微雨的傍晚走过街角。\",\"sc\":\"MUSIC #0324\"}"));
        l.add(new Record(MUSIC, "富士山下", "陳奕迅", "2026.08.30", null,
                "誰都只得那雙手\n靠擁抱亦難任你擁有\n要擁有必先懂失去怎接受",
                "#3B4D5E", "#D6C5A9",
                "{\"fid\":\"serif\",\"tw\":0,\"lsz\":21.0,\"bl\":1,\"th\":\"渡轮靠岸前耳机里刚好放到这一首。\",\"sc\":\"MUSIC #0409\"}"));
        l.add(new Record(TICKET, "陽台", "张猛", "2026.09.24", "4.5",
                "坐在陽台上，看著遠處的屋頂與晚霞，啊…我好想就此好好老去。\n能跟上老去的速度，大概多半是心願未了。",
                "#3B3B3B", "#CFCFCF", "万达影城", "3号厅", "E排 8座", "IMAX", "¥65", "21:10",
                "{\"fm\":0,\"nl\":0,\"es\":4,\"pt\":0,\"tt\":2,\"nb\":false,\"tsz\":28,\"bsz\":11.5,\"sc\":\"ID #004238\"}"));
        l.add(new Record(TICKET, "麻雀", "杜琪峰", "2026.09.18", "4.7",
                "今天在路邊撿到一隻樹上掉下來的小麻雀。還好牠自己也奮力撲翅想要抓住外表光滑的車子。\n牠在手裡看起來很冷靜，只叫了一次去回應樹上的同伴。",
                "#4A524E", "#B8C0BC", "百老汇影城", "2号厅", "D排 10座", "胶片修复", "¥55", "19:00",
                "{\"fm\":0,\"nl\":1,\"es\":4,\"pt\":0,\"tt\":1,\"nb\":true,\"tsz\":30,\"bsz\":11.0,\"sc\":\"ID #004241\"}"));
        l.add(new Record(FOOTPRINT, "油麻地街口", "香港 · 九龙", "2026.09.24", null,
                "散场后走过老街。",
                "#4A6670", "#D8C8B0",
                "16:10|午后的旧书店里很安静。",
                "23:05|散场后霓虹灯还亮着，街角有人在等红灯。"));
        l.add(new Record(BOOK, "太阳照常升起", "海明威", "2026.10.04", null,
                "她自己也以为她的痛苦久已钝化了，\n但是那痛苦似乎是她身体里\n唯一的有生命力的东西，\n永远是新鲜强烈的，\n一发作起来就不给她片刻的休息。",
                "#6B7A6A", "#C9CDB8",
                "{\"ns\":0,\"pt\":0,\"fid\":\"sketch\",\"tsz\":15.0,\"ca\":true,\"th\":\"深夜读到这一段的时候，窗外刚好起风了。有些情绪总是在安静下来时才显出原本的轮廓。\",\"stc\":false,\"sc\":\"BOOK #0108\"}"));
        l.add(new Record(BOOK, "小径分岔的花园", "博尔赫斯", "2026.09.24", null,
                "时间有无数种可能，\n而我们只在其中一条里相遇。\n在大部分时间里，我们并不存在；\n在某些时间，有你而没有我。",
                "#6B7A6A", "#C9CDB8",
                "{\"ns\":1,\"pt\":0,\"fid\":\"serif\",\"tsz\":14.5,\"ca\":false,\"th\":\"在旧书店靠窗的位置读完这篇，拍下了窗台上的光影。\",\"stc\":false,\"sc\":\"BOOK #0214\"}"));

        // 08月30日: 足迹 + 电影 + 书 + 声音
        l.add(new Record(FOOTPRINT, "长洲", "香港 · 长洲岛", "2026.08.30", null,
                "最后一班渡轮之前,在海边坐了一会儿。",
                "#5A7D8C", "#E6DCC8",
                "12:07|到岛上的时候刚好是正午,路很窄,猫比人多。",
                "15:32|在海边坐了一会儿,什么都没想。",
                "18:20|最后一班渡轮之前,夕阳落在水面上,慢慢变成一条橘色的线。"));
        l.add(new Record(TICKET, "东邪西毒", "王家卫", "2026.08.30", "4.8",
                "在一個非常嚴苛的角度，記憶奇蹟似地彈射出去了，\n每個人都有煩惱，只是因為記性太好，我們不要那麼快失望。",
                "#B07A33", "#2A2118", "百老汇影城", "1号厅", "H排 6座", "2D 修复版", "¥55", "20:00",
                "{\"fm\":0,\"nl\":1,\"es\":2,\"pt\":0,\"tt\":1,\"nb\":false,\"tsz\":28,\"bsz\":11.0,\"sc\":\"ID #004245\"}"));
        l.add(new Record(BOOK, "小王子", "圣埃克苏佩里", "2026.08.30", null,
                "真正重要的东西，\n用眼睛是看不见的。\n正是你为你的玫瑰花费的时间，\n才使你的玫瑰变得如此重要。",
                "#C9A24B", "#F1E3B8",
                "{\"ns\":2,\"pt\":1,\"fid\":\"sketch\",\"tsz\":15.5,\"ca\":true,\"th\":\"渡轮上重读的一页，海面上的夕阳像极了B612星球。\",\"stc\":false,\"sc\":\"BOOK #0329\"}"));
        l.add(new Record(BOOK, "漫长的告别", "雷蒙德·Chandler", "2026.07.18", null,
                "说一声再见，就是死去一点点。\n哪怕街角的霓虹灯还亮着，\n昨天喝过的咖啡杯也已经凉透了。",
                "#8E4F4B", "#E5C185",
                "{\"ns\":3,\"pt\":0,\"fid\":\"serif\",\"tsz\":15.0,\"ca\":true,\"th\":\"雨夜合上书页，拍下了书封和桌上的台灯。\",\"stc\":false,\"sc\":\"BOOK #0412\"}"));
        l.add(new Record(VOICE, "外婆厨房里的收音机与切菜声", "老家 · 午后厨房", "2026.08.30", null,
                "夏天午后吊扇嗡嗡转着，旧收音机里咿咿呀呀地唱着粤曲，案板上切菜声笃笃笃地响。录下了这几十秒，一听就像回到了小时候。",
                "#8E4F4B", "#E5C185", "01:18", "本地上传 · 手机语音备忘录"));

        // 05月02日: 足迹 + 多部电影
        l.add(new Record(FOOTPRINT, "银阁寺", "日本 · 京都", "2026.05.02", null,
                "银阁寺本尊,比我想的小。",
                "#5E8C3A", "#C8D98A",
                "12:07|银阁寺本尊,比我想的小,不知道为什么叫银阁寺呢?里面是银做的吗 0.o",
                "12:15|苔藓园很安静,光从树叶缝里漏下来。"));
        l.add(new Record(TICKET, "About Time", "Richard Curtis", "2026.05.02", "4.8",
                "我們生活的每一天，都在一起穿越時空，\n我們所能做的就是盡全力品味這段不凡的旅程。",
                "#7A1F2B", "#E4C9A8", "百老汇影城", "2号厅", "F排 9座", "2D", "¥52", "19:30",
                "{\"fm\":0,\"nl\":3,\"es\":2,\"pt\":2,\"tt\":3,\"nb\":false,\"tsz\":26,\"bsz\":11.5,\"sc\":\"ID #004234\"}"));
        l.add(new Record(TICKET, "寄生虫", "奉俊昊", "2026.05.02", "4.7",
                "看新聞或網路媒體，我注意到那些無法忽視的「背後的真相」。\n樓梯一路向下，雨也是，沒有計劃就是最好的計劃。",
                "#2F3A2E", "#B9C7A3", "UME影城", "4号厅", "D排 5座", "2D", "¥45", "21:30",
                "{\"fm\":0,\"nl\":0,\"es\":3,\"pt\":3,\"tt\":0,\"nb\":false,\"tsz\":28,\"bsz\":11.5,\"sc\":\"ID #004249\"}"));
        l.add(new Record(TICKET, "花束般的恋爱", "土井裕泰", "2026.03.21", "4.4",
                "兩個人的流水賬，最後都是回不去的日常。",
                "#4A7FA8", "#F0D9C4", "万达影城", "6号厅", "C排 11座", "2D", "¥58", "16:20",
                "{\"fm\":2,\"nl\":0,\"es\":1,\"pt\":0,\"tt\":0,\"nb\":false,\"tsz\":24,\"bsz\":11.5,\"sc\":\"ID #004252\"}"));
        l.add(new Record(VOICE, "After the Storm in Taipei", "台北 · 雨后巷口", "2026.06.14", null,
                "台风过境后的清晨，屋檐滴水声与远处机车驶过积水路面的声音交织在一起，空气里全是潮湿的樟树叶气味。",
                "#3E4A45", "#C9C2AE", "02:35", "现场录音 · 随身采集"));
        l.add(new Record(VOICE, "清晨果园与第一班列车", "长野 · 山间月台", "2026.04.19", null,
                "清晨五点半的无人小站，露水挂在苹果枝头，远处传来踏切叮当叮当的提示音和早班电车进站的风声。",
                "#5A7D8C", "#E7A35A", "01:06", "现场录音 · 随身采集"));
        long now = System.currentTimeMillis();
        for (int i = 0; i < l.size(); i++) {
            Record r = l.get(i);
            r.id = SAMPLE_PREFIX + i;
            r.createdAt = r.updatedAt = now - i;
        }
        return l;
    }
}
