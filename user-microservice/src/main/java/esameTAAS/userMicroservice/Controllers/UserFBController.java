package esameTAAS.userMicroservice.Controllers;


import esameTAAS.userMicroservice.Models.AccessToken;
import esameTAAS.userMicroservice.Models.Comunication.BasicInfoUser;
import esameTAAS.userMicroservice.Models.Comunication.FacebookInfoUser;
import esameTAAS.userMicroservice.Models.PlatformUser;
import esameTAAS.userMicroservice.Repositories.AccessTokenRepository;
import esameTAAS.userMicroservice.Repositories.UserFBRepository;
import esameTAAS.userMicroservice.UserMicroServiceApplication;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import esameTAAS.userMicroservice.Models.ResponseStatus;
import org.springframework.web.bind.annotation.*;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static esameTAAS.userMicroservice.Models.PlatformUser.getMailFromToken;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1")
public class UserFBController {
    private final RabbitTemplate rabbitTemplate;

    public UserFBController( RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Autowired
    private UserFBRepository userFBRepository;
    @Autowired
    private AccessTokenRepository accessTokenRepository;

    @GetMapping("/users")   //TODO testing api
    public List<PlatformUser> list(){
        return userFBRepository.findAll();
    }

    @GetMapping("/user/token/{username}") //TODO testing api
    public List<AccessToken> getLastToken(@PathVariable("username") String username){
        return accessTokenRepository.findLastAccessTokenByUsername(username);
    }

    @GetMapping("/token")   //TODO testing api
    public List<AccessToken> getToken(){
        return accessTokenRepository.findAll();
    }

    @GetMapping("/user/{value}")
    public PlatformUser userFromUsername(@PathVariable("value") String value){
       return userFBRepository.findUserByUsername(value);
    }

    @PostMapping("/user")
    public ResponseEntity<String> getUser(@RequestBody FacebookInfoUser userOBJ) {
        System.out.println("#############---->ENTRO NELL'API");
        PlatformUser user = accessTokenRepository.findUserByToken(userOBJ.getToken());
        AccessToken accessToken;
        Date parsedDate = null;
        System.out.println("#############---->"+user);
        System.out.println("#############---->"+userOBJ.getToken());
        if(user == null){
            try {
                System.out.println("#############---->"+userOBJ.getToken());
                PlatformUser userTMP = new PlatformUser();
                accessToken = userTMP.initUserFB(userOBJ.getToken(), "facebookInfoUser.getUsername()"); //TODO: Errore qui non capisco come mai non validi il token (stesso token si valida a riga 118)
                System.out.println("#############---->"+accessToken.toString());
                System.out.println("#############---->"+userTMP.getEmail());
                String email = getMailFromToken(userOBJ.getToken()); //search email from token
                System.out.println("#############---->"+email);
                if(email == null) //check of email exist
                    return new ResponseEntity<>(ResponseStatus.BAD_REQUEST_USER_NOT_EXIST.defaultDescription, HttpStatus.INTERNAL_SERVER_ERROR);
                user = userFBRepository.findUserByEmail(email); //get user from email
                System.out.println("#############---->"+user);
                if(user == null) //check if user exist in database
                    return new ResponseEntity<>(ResponseStatus.BAD_REQUEST_USER_NOT_EXIST.defaultDescription, HttpStatus.INTERNAL_SERVER_ERROR);
                System.out.println("#############---->"+user.toString());
                accessToken = user.initUserFB(userOBJ.getToken(),user.getUsername()); //get access token
                System.out.println("#############---->"+accessToken.toString());
                accessTokenRepository.save(accessToken);
                rabbitTemplate.convertAndSend(UserMicroServiceApplication.topicExchangeName, "userOBJ.getToken()-exchange", accessToken.serialize());
                return new ResponseEntity<>(user.toString() + accessToken.toString(), HttpStatus.OK);

            } catch (Exception e) {
                e.printStackTrace();
                return new ResponseEntity<>(ResponseStatus.BAD_REQUEST_USER_NOT_EXIST.defaultDescription, HttpStatus.INTERNAL_SERVER_ERROR);
            }

        }
        accessToken = accessTokenRepository.findAccessTokenByToken(userOBJ.getToken());
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);
        try {
            parsedDate = dateFormat.parse(accessToken.getExpiring_date());
            if(parsedDate.after(new Date(System.currentTimeMillis()))){
                return new ResponseEntity<>(user.toString() + accessToken.toString(), HttpStatus.OK);
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return new ResponseEntity<>(ResponseStatus.UNAUTHORIZED_TOKEN_EXPIRED.defaultDescription, HttpStatus.OK);
    }

    @PostMapping("/loginFB")
    public ResponseEntity<String> loginFB(@RequestBody FacebookInfoUser facebookInfoUser) {
        PlatformUser user = new PlatformUser();
        AccessToken accessToken;
        ResponseStatus result;
        result = PlatformUser.checkUsername(facebookInfoUser.getUsername());
        if (!result.equals(ResponseStatus.OK)) //Check username
            return new ResponseEntity<>(result.defaultDescription, HttpStatus.BAD_REQUEST);
        try {
            accessToken = user.initUserFB(facebookInfoUser.getToken(), facebookInfoUser.getUsername());
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(ResponseStatus.ERROR_FACEBOOK.defaultDescription, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        if (userFBRepository.findUserByEmail(user.getEmail()) == null) { //Check if user already insert into DB
            if (userFBRepository.findUserByUsername(facebookInfoUser.getUsername()) == null) { //Check if username is correct
                userFBRepository.save(user);
            } else {
                return new ResponseEntity<>(ResponseStatus.ERROR_FACEBOOK.defaultDescription, HttpStatus.INTERNAL_SERVER_ERROR);

            }
        }
        if(accessTokenRepository.findAccessTokenByToken(facebookInfoUser.getToken()) == null) //Check if the token already exist
            accessTokenRepository.save(accessToken);
        rabbitTemplate.convertAndSend(UserMicroServiceApplication.topicExchangeName, "token-exchange", accessToken.serialize());
        return new ResponseEntity<>(user.toString() + accessToken.toString(), HttpStatus.OK);
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody BasicInfoUser basicInfoUser) {
        if (userFBRepository.findUserByUsername(basicInfoUser.getUsername()) == null) { //Check if user exist
            return new ResponseEntity<>(ResponseStatus.BAD_REQUEST_USER_NOT_EXIST.defaultDescription, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        PlatformUser user = userFBRepository.findUserByUsername(basicInfoUser.getUsername());
        if(Objects.equals(user.getPassword(), basicInfoUser.getPassword())){
            AccessToken accessToken = new AccessToken();
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH); //Declare date format
            Calendar expiringDate = Calendar.getInstance();
            expiringDate.setTime(new Date());
            if(basicInfoUser.getRemember_me())
                expiringDate.add(Calendar.DATE, 30); //set the expiring Date
            else
                expiringDate.add(Calendar.DATE, 1); //set the expiring Date
            accessToken.initAccessToken(UUID.randomUUID().toString(),user.getUsername(),dateFormat.format(expiringDate.getTime())); // Init Access token
            if(accessTokenRepository.findAccessTokenByToken(accessToken.getToken()) == null) //Check if the token already exist
                accessTokenRepository.save(accessToken);
            rabbitTemplate.convertAndSend(UserMicroServiceApplication.topicExchangeName, "token-exchange", accessToken.serialize());
            return new ResponseEntity<>(user.toString() + accessToken.toString(), HttpStatus.OK);
        }
        return new ResponseEntity<>(ResponseStatus.BAD_REQUEST_WRONG_PASSWORD.defaultDescription, HttpStatus.OK);

    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody PlatformUser user) {
        ResponseStatus result = PlatformUser.checkUsername(user.getUsername());
        if (!result.equals(ResponseStatus.OK)) //Check username
            return new ResponseEntity<>(result.defaultDescription, HttpStatus.BAD_REQUEST);
        if (userFBRepository.findUserByEmail(user.getEmail()) == null) { //Check if user already insert into DB
            if (userFBRepository.findUserByUsername(user.getUsername()) == null) { //Check if username is correct
                userFBRepository.save(user);
                login(new BasicInfoUser(user.getUsername(),user.getPassword(),false));
            } else {
                return new ResponseEntity<>(ResponseStatus.ERROR_FACEBOOK.defaultDescription, HttpStatus.INTERNAL_SERVER_ERROR);

            }
        }
        return new ResponseEntity<>(ResponseStatus.ERROR_REGISTER.defaultDescription, HttpStatus.INTERNAL_SERVER_ERROR);

    }


}
