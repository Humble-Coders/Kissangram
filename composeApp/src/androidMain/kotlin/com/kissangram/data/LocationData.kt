package com.kissangram.data

/**
 * Reference data for India states and districts.
 * Used for populating location dropdowns in Edit Profile screen.
 * This data can be uploaded to Firestore using the upload button in HomeScreen (dev only).
 */
object LocationData {
    val indiaStatesAndDistricts: Map<String, List<String>> = mapOf(
        "Andhra Pradesh" to listOf(
            "Alluri Sitharama Raju", "Anakapalli", "Ananthapuramu", "Annamayya",
            "Bapatla", "Chittoor", "Dr. B.R. Ambedkar Konaseema", "East Godavari",
            "Eluru", "Guntur", "Kakinada", "Krishna", "Kurnool", "NTR", "Nandyal",
            "Palnadu", "Parvathipuram Manyam", "Prakasam", "Sri Potti Sriramulu Nellore",
            "Sri Sathya Sai", "Srikakulam", "Tirupati", "Visakhapatnam", "Vizianagaram",
            "West Godavari", "YSR"
        ),
        "Arunachal Pradesh" to listOf(
            "Anjaw", "Changlang", "East Kameng", "East Siang", "Itanagar capital complex",
            "Kamle", "Kra Daadi", "Kurung Kumey", "Lepa Rada", "Lohit", "Longding",
            "Lower Dibang Valley", "Lower Siang", "Lower Subansiri", "Namsai",
            "Pakke-Kessang", "Papum Pare", "Shi Yomi", "Siang", "Tawang", "Tirap",
            "Upper Dibang Valley", "Upper Siang", "Upper Subansiri", "West Kameng", "West Siang"
        ),
        "Assam" to listOf(
            "Baksa", "Barpeta", "Bongaigaon", "Cachar", "Charaideo", "Chirang",
            "Darrang", "Dhemaji", "Dhubri", "Dibrugarh", "Dima Hasao", "Goalpara",
            "Golaghat", "Hailakandi", "Jorhat", "Kamrup", "Kamrup Metropolitan",
            "Karbi Anglong", "Karimganj", "Kokrajhar", "Lakhimpur", "Majuli",
            "Morigaon", "Nagaon", "Nalbari", "Sivasagar", "Sonitpur",
            "South Salmara Mankachar", "Tinsukia", "Udalguri", "West Karbi Anglong"
        ),
        "Bihar" to listOf(
            "Araria", "Arwal", "Aurangabad", "Banka", "Begusarai", "Bhagalpur",
            "Bhojpur", "Buxar", "Darbhanga", "East Champaran", "Gaya", "Gopalganj",
            "Jamui", "Jehanabad", "Kaimur", "Katihar", "Khagaria", "Kishanganj",
            "Lakhisarai", "Madhepura", "Madhubani", "Munger", "Muzaffarpur", "Nalanda",
            "Nawada", "Patna", "Purnia", "Rohtas", "Saharsa", "Samastipur", "Saran",
            "Sheikhpura", "Sheohar", "Sitamarhi", "Siwan", "Supaul", "Vaishali", "West Champaran"
        ),
        "Chhattisgarh" to listOf(
            "Balod", "Baloda Bazar", "Balrampur-Ramanujganj", "Bastar", "Bemetara",
            "Bijapur", "Bilaspur", "Dantewada", "Dhamtari", "Durg", "Gariaband",
            "Gaurela-Pendra-Marwahi", "Janjgir-Champa", "Jashpur", "Kabirdham", "Kanker",
            "Khairagarh-Chhuikhadan-Gandai", "Kondagaon", "Korba", "Korea", "Mahasamund",
            "Manendragarh-Chirmiri-Bharatpur", "Mohla-Manpur-Ambagarh Chowki", "Mungeli",
            "Narayanpur", "Raigarh", "Raipur", "Rajnandgaon", "Sarangarh-Bilaigarh",
            "Shakti", "Sukma", "Surajpur", "Surguja"
        ),
        "Goa" to listOf("North Goa", "South Goa"),
        "Gujarat" to listOf(
            "Ahmedabad", "Amreli", "Anand", "Aravalli", "Banaskantha", "Bharuch",
            "Bhavnagar", "Botad", "Chhota Udaipur", "Dahod", "Dang", "Devbhumi Dwarka",
            "Gandhinagar", "Gir Somnath", "Jamnagar", "Junagadh", "Kheda", "Kutch",
            "Mahisagar", "Mehsana", "Morbi", "Narmada", "Navsari", "Panchmahal",
            "Patan", "Porbandar", "Rajkot", "Sabarkantha", "Surat", "Surendranagar",
            "Tapi", "Vadodara", "Valsad"
        ),
        "Haryana" to listOf(
            "Ambala", "Bhiwani", "Charkhi Dadri", "Faridabad", "Fatehabad", "Gurugram",
            "Hisar", "Jhajjar", "Jind", "Kaithal", "Karnal", "Kurukshetra", "Mahendragarh",
            "Nuh", "Palwal", "Panchkula", "Panipat", "Rewari", "Rohtak", "Sirsa",
            "Sonipat", "Yamunanagar"
        ),
        "Himachal Pradesh" to listOf(
            "Bilaspur", "Chamba", "Hamirpur", "Kangra", "Kinnaur", "Kullu",
            "Lahaul and Spiti", "Mandi", "Shimla", "Sirmaur", "Solan", "Una"
        ),
        "Jharkhand" to listOf(
            "Bokaro", "Chatra", "Deoghar", "Dhanbad", "Dumka", "East Singhbhum",
            "Garhwa", "Giridih", "Godda", "Gumla", "Hazaribag", "Jamtara", "Khunti",
            "Koderma", "Latehar", "Lohardaga", "Pakur", "Palamu", "Ramgarh", "Ranchi",
            "Sahibganj", "Seraikela-Kharsawan", "Simdega", "West Singhbhum"
        ),
        "Karnataka" to listOf(
            "Bagalakote", "Ballari", "Belagavi", "Bengaluru Rural", "Bengaluru Urban",
            "Bidar", "Chamarajanagara", "Chikkaballapura", "Chikkamagaluru", "Chitradurga",
            "Dakshina Kannada", "Davanagere", "Dharwada", "Gadaga", "Hassan", "Haveri",
            "Kalaburagi", "Kodagu", "Kolar", "Koppala", "Mandya", "Mysuru", "Raichuru",
            "Ramanagara", "Shivamogga", "Tumakuru", "Udupi", "Uttara Kannada",
            "Vijayanagara", "Vijayapura", "Yadgiri"
        ),
        "Kerala" to listOf(
            "Alappuzha", "Ernakulam", "Idukki", "Kannur", "Kasaragod", "Kollam",
            "Kottayam", "Kozhikode", "Malappuram", "Palakkad", "Pathanamthitta",
            "Thiruvananthapuram", "Thrissur", "Wayanad"
        ),
        "Madhya Pradesh" to listOf(
            "Agar Malwa", "Alirajpur", "Anuppur", "Ashoknagar", "Balaghat", "Barwani",
            "Betul", "Bhind", "Bhopal", "Burhanpur", "Chhatarpur", "Chhindwara", "Damoh",
            "Datia", "Dewas", "Dhar", "Dindori", "Guna", "Gwalior", "Harda", "Hoshangabad",
            "Indore", "Jabalpur", "Jhabua", "Katni", "Khandwa", "Khargone", "Mandla",
            "Mandsaur", "Morena", "Narsinghpur", "Neemuch", "Niwari", "Panna", "Raisen",
            "Rajgarh", "Ratlam", "Rewa", "Sagar", "Satna", "Sehore", "Seoni", "Shahdol",
            "Shajapur", "Sheopur", "Shivpuri", "Sidhi", "Singrauli", "Tikamgarh", "Ujjain",
            "Umaria", "Vidisha"
        ),
        "Maharashtra" to listOf(
            "Ahmednagar", "Akola", "Amravati", "Aurangabad", "Beed", "Bhandara",
            "Buldhana", "Chandrapur", "Dhule", "Gadchiroli", "Gondia", "Hingoli",
            "Jalgaon", "Jalna", "Kolhapur", "Latur", "Mumbai City", "Mumbai Suburban",
            "Nagpur", "Nanded", "Nandurbar", "Nashik", "Osmanabad", "Palghar", "Parbhani",
            "Pune", "Raigad", "Ratnagiri", "Sangli", "Satara", "Sindhudurg", "Solapur",
            "Thane", "Wardha", "Washim", "Yavatmal"
        ),
        "Manipur" to listOf(
            "Bishnupur", "Chandel", "Churachandpur", "Imphal East", "Imphal West",
            "Jiribam", "Kakching", "Kamjong", "Kangpokpi", "Noney", "Pherzawl",
            "Senapati", "Tamenglong", "Tengnoupal", "Thoubal", "Ukhrul"
        ),
        "Meghalaya" to listOf(
            "East Garo Hills", "East Jaintia Hills", "East Khasi Hills",
            "Eastern West Khasi Hills", "North Garo Hills", "Ri Bhoi", "South Garo Hills",
            "South West Garo Hills", "South West Khasi Hills", "West Garo Hills",
            "West Jaintia Hills", "West Khasi Hills"
        ),
        "Mizoram" to listOf(
            "Aizawl", "Champhai", "Hnahthial", "Khawzawl", "Kolasib", "Lawngtlai",
            "Lunglei", "Mamit", "Saiha", "Saitual", "Serchhip"
        ),
        "Nagaland" to listOf(
            "Chümoukedima", "Dimapur", "Kiphire", "Kohima", "Longleng", "Mokokchung",
            "Mon", "Niuland", "Noklak", "Peren", "Phek", "Shamator", "Tseminyü",
            "Tuensang", "Wokha", "Zunheboto"
        ),
        "Odisha" to listOf(
            "Angul", "Balangir", "Balasore", "Bargarh", "Bhadrak", "Boudh", "Cuttack",
            "Debagarh", "Dhenkanal", "Gajapati", "Ganjam", "Jagatsinghpur", "Jajpur",
            "Jharsuguda", "Kalahandi", "Kandhamal", "Kendrapara", "Kendujhar", "Khordha",
            "Koraput", "Malkangiri", "Mayurbhanj", "Nabarangpur", "Nayagarh", "Nuapada",
            "Puri", "Rayagada", "Sambalpur", "Subarnapur", "Sundargarh"
        ),
        "Punjab" to listOf(
            "Amritsar", "Barnala", "Bathinda", "Faridkot", "Fatehgarh Sahib", "Fazilka",
            "Firozpur", "Gurdaspur", "Hoshiarpur", "Jalandhar", "Kapurthala", "Ludhiana",
            "Malerkotla", "Mansa", "Moga", "Pathankot", "Patiala", "Rupnagar",
            "Sahibzada Ajit Singh Nagar", "Sangrur", "Shahid Bhagat Singh Nagar",
            "Sri Muktsar Sahib", "Tarn Taran"
        ),
        "Rajasthan" to listOf(
            "Ajmer", "Alwar", "Banswara", "Baran", "Barmer", "Bharatpur", "Bhilwara",
            "Bikaner", "Bundi", "Chittorgarh", "Churu", "Dausa", "Dholpur", "Dungarpur",
            "Hanumangarh", "Jaipur", "Jaisalmer", "Jalore", "Jhalawar", "Jhunjhunu",
            "Jodhpur", "Karauli", "Kota", "Nagaur", "Pali", "Pratapgarh", "Rajsamand",
            "Sawai Madhopur", "Sikar", "Sirohi", "Sri Ganganagar", "Tonk", "Udaipur"
        ),
        "Sikkim" to listOf(
            "East Sikkim", "North Sikkim", "Pakyong", "Soreng", "South Sikkim", "West Sikkim"
        ),
        "Tamil Nadu" to listOf(
            "Ariyalur", "Chengalpattu", "Chennai", "Coimbatore", "Cuddalore", "Dharmapuri",
            "Dindigul", "Erode", "Kallakurichi", "Kanchipuram", "Kanyakumari", "Karur",
            "Krishnagiri", "Madurai", "Mayiladuthurai", "Nagapattinam", "Namakkal",
            "Nilgiris", "Perambalur", "Pudukkottai", "Ramanathapuram", "Ranipet", "Salem",
            "Sivaganga", "Tenkasi", "Thanjavur", "Theni", "Thoothukudi", "Tiruchirappalli",
            "Tirunelveli", "Tirupattur", "Tiruppur", "Tiruvallur", "Tiruvannamalai",
            "Tiruvarur", "Vellore", "Viluppuram", "Virudhunagar"
        ),
        "Telangana" to listOf(
            "Adilabad", "Bhadradri Kothagudem", "Hanamkonda", "Hyderabad", "Jagtial",
            "Jangaon", "Jayashankar Bhupalpally", "Jogulamba Gadwal", "Kamareddy",
            "Karimnagar", "Khammam", "Kumuram Bheem Asifabad", "Mahabubabad", "Mahbubnagar",
            "Mancherial", "Medak", "Medchal–Malkajgiri", "Mulugu", "Nagarkurnool",
            "Nalgonda", "Narayanpet", "Nirmal", "Nizamabad", "Peddapalli",
            "Rajanna Sircilla", "Ranga Reddy", "Sangareddy", "Siddipet", "Suryapet",
            "Vikarabad", "Wanaparthy", "Warangal", "Yadadri Bhuvanagiri"
        ),
        "Tripura" to listOf(
            "Dhalai", "Gomati", "Khowai", "North Tripura", "Sepahijala", "South Tripura",
            "Unakoti", "West Tripura"
        ),
        "Uttar Pradesh" to listOf(
            "Agra", "Aligarh", "Ambedkar Nagar", "Amethi", "Amroha", "Auraiya", "Ayodhya",
            "Azamgarh", "Bagpat", "Bahraich", "Ballia", "Balrampur", "Banda", "Barabanki",
            "Bareilly", "Basti", "Bhadohi", "Bijnor", "Budaun", "Bulandshahr", "Chandauli",
            "Chitrakoot", "Deoria", "Etah", "Etawah", "Farrukhabad", "Fatehpur", "Firozabad",
            "Gautam Buddha Nagar", "Ghaziabad", "Ghazipur", "Gonda", "Gorakhpur", "Hamirpur",
            "Hapur", "Hardoi", "Hathras", "Jalaun", "Jaunpur", "Jhansi", "Kannauj",
            "Kanpur Dehat", "Kanpur Nagar", "Kasganj", "Kaushambi", "Kushinagar",
            "Lakhimpur Kheri", "Lalitpur", "Lucknow", "Maharajganj", "Mahoba", "Mainpuri",
            "Mathura", "Mau", "Meerut", "Mirzapur", "Moradabad", "Muzaffarnagar", "Pilibhit",
            "Pratapgarh", "Prayagraj", "Raebareli", "Rampur", "Saharanpur", "Sambhal",
            "Sant Kabir Nagar", "Shahjahanpur", "Shamli", "Shravasti", "Siddharthnagar",
            "Sitapur", "Sonbhadra", "Sultanpur", "Unnao", "Varanasi"
        ),
        "Uttarakhand" to listOf(
            "Almora", "Bageshwar", "Chamoli", "Champawat", "Dehradun", "Haridwar",
            "Nainital", "Pauri Garhwal", "Pithoragarh", "Rudraprayag", "Tehri Garhwal",
            "Udham Singh Nagar", "Uttarkashi"
        ),
        "West Bengal" to listOf(
            "Alipurduar", "Bankura", "Birbhum", "Cooch Behar", "Dakshin Dinajpur",
            "Darjeeling", "Hooghly", "Howrah", "Jalpaiguri", "Jhargram", "Kalimpong",
            "Kolkata", "Maldah", "Murshidabad", "Nadia", "North 24 Parganas",
            "Paschim Bardhaman", "Paschim Medinipur", "Purba Bardhaman", "Purba Medinipur",
            "Purulia", "South 24 Parganas", "Uttar Dinajpur"
        ),
        "Andaman and Nicobar" to listOf(
            "Nicobar", "North and Middle Andaman", "South Andaman"
        ),
        "Chandigarh" to listOf("Chandigarh"),
        "Dadra and Nagar Haveli and Daman and Diu" to listOf(
            "Dadra and Nagar Haveli", "Daman", "Diu"
        ),
        "Jammu and Kashmir" to listOf(
            "Anantnag", "Bandipore", "Baramulla", "Budgam", "Doda", "Ganderbal", "Jammu",
            "Kathua", "Kishtwar", "Kulgam", "Kupwara", "Poonch", "Pulwama", "Rajouri",
            "Ramban", "Reasi", "Samba", "Shopian", "Srinagar", "Udhampur"
        ),
        "Ladakh" to listOf("Kargil", "Leh"),
        "Lakshadweep" to listOf("Lakshadweep"),
        "National Capital Territory of Delhi" to listOf(
            "Central Delhi", "East Delhi", "New Delhi", "North Delhi", "North East Delhi",
            "North West Delhi", "Shahdara district", "South Delhi", "South East Delhi",
            "South West Delhi", "West Delhi"
        ),
        "Puducherry" to listOf("Karaikal", "Mahé", "Puducherry", "Yanam")
    )
    
    /**
     * Get sorted list of state names
     */
    val stateNames: List<String> = indiaStatesAndDistricts.keys.sorted()
    
    /**
     * Get districts for a given state
     */
    fun getDistricts(state: String): List<String> {
        return indiaStatesAndDistricts[state] ?: emptyList()
    }
}
