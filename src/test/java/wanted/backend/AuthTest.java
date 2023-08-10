package wanted.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import wanted.backend.Domain.Member.AuthDto;
import wanted.backend.Domain.Member.Member;
import wanted.backend.Domain.Member.TokenDto;
import wanted.backend.Domain.ResponseDto;
import wanted.backend.Jwt.JwtUtil;
import wanted.backend.Repository.MemberRepository;
import wanted.backend.Repository.RefreshTokenRepository;
import wanted.backend.Service.AuthService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Authorization Test")
public class AuthTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("회원가입 - 성공")
    void join() {

        AuthDto authDto = new AuthDto("test@gmail.com", "test123!");

        authService.joinMember(authDto);

        Member findMember = memberRepository.findAll().get(0);
        Assertions.assertEquals(memberRepository.count(), 1);
        Assertions.assertEquals(findMember.getEmail(), authDto.getEmail());

    }

    @Test
    @DisplayName("회원가입 - 유효하지 않은 이메일")
    void joinInvalidEmail() {

        AuthDto authDto = new AuthDto("test", "test123!");

        ResponseDto responseDto = authService.joinMember(authDto);

        Assertions.assertEquals(responseDto.getMessage(), "invalid email");
        Assertions.assertEquals(memberRepository.count(), 0);

    }

    @Test
    @DisplayName("회원가입 - 유효하지 않은 비밀번호")
    void joinInvalidPassword() {

        AuthDto authDto = new AuthDto("test@gmail.com", "test");

        ResponseDto responseDto = authService.joinMember(authDto);

        Assertions.assertEquals(responseDto.getMessage(), "invalid password");
        Assertions.assertEquals(memberRepository.count(), 0);

    }

    @Test
    @DisplayName("회원가입 - 중복된 이메일")
    void joinDuplicatedEmail() {

        AuthDto authDto1 = new AuthDto("test@gmail.com", "test123!");
        AuthDto authDto2 = new AuthDto("test@gmail.com", "test123!");

        ResponseDto responseDto1 = authService.joinMember(authDto1);
        ResponseDto responseDto2 = authService.joinMember(authDto2);

        Assertions.assertEquals(responseDto1.getStatus(), HttpStatus.OK);
        Assertions.assertEquals(responseDto2.getStatus(), HttpStatus.CONFLICT);
        Assertions.assertEquals(memberRepository.count(), 1);
    }

    @Test
    @DisplayName("로그인 - 성공")
    void login() throws Exception {

        // given
        ObjectMapper objectMapper = new ObjectMapper();

        AuthDto authDto = new AuthDto("test@gmail.com", "test123!");
        authService.joinMember(authDto);

        // when
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authDto)))
                    .andExpect(status().isOk())
                    .andExpect(cookie().exists("Access_Token"))
                    .andExpect(cookie().exists("Refresh_Token"))
                    .andReturn();

        // then
        String cookieAccessToken = result.getResponse().getCookie("Access_Token").getValue();
        String cookieRefreshToken = result.getResponse().getCookie("Refresh_Token").getValue();

        String content = result.getResponse().getContentAsString();
        ResponseDto responseDto = objectMapper.readValue(content, ResponseDto.class);

        TokenDto responseToken = objectMapper.convertValue(responseDto.getData(), TokenDto.class);

        Assertions.assertEquals(responseToken.getAccessToken(), cookieAccessToken);
        Assertions.assertEquals(responseToken.getRefreshToken(), cookieRefreshToken);
        Assertions.assertEquals(refreshTokenRepository.count(), 1);
    }

    @Test
    @DisplayName("로그인 - 유효하지 않은 이메일")
    void loginInvalidEmail() throws Exception {

        // given
        ObjectMapper objectMapper = new ObjectMapper();

        AuthDto saveDto = new AuthDto("test@gmail.com", "test123!");
        AuthDto loginDto = new AuthDto("test", "test123!");
        authService.joinMember(saveDto);

        // when
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                    .andExpect(status().isBadRequest())
                    .andReturn();

        // then
        String content = result.getResponse().getContentAsString();
        String message = objectMapper.readValue(content, ResponseDto.class).getMessage();
        Assertions.assertEquals(message, "invalid email");
    }
    @Test
    @DisplayName("로그인 - 유효하지 않은 비밀번호")
    void loginInvalidPassword() throws Exception {

        // given
        ObjectMapper objectMapper = new ObjectMapper();

        AuthDto saveDto = new AuthDto("test@gmail.com", "test123!");
        AuthDto loginDto = new AuthDto("test@gmail.com", "test");
        authService.joinMember(saveDto);

        // when
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                    .andExpect(status().isBadRequest())
                    .andReturn();

        // then
        String content = result.getResponse().getContentAsString();
        String message = objectMapper.readValue(content, ResponseDto.class).getMessage();
        Assertions.assertEquals(message, "invalid password");
    }

    @Test
    @DisplayName("로그인 - 실패")
    void loginFail() throws Exception {

        // given
        ObjectMapper objectMapper = new ObjectMapper();

        AuthDto saveDto = new AuthDto("test@gmail.com", "test123!");
        AuthDto loginDto = new AuthDto("test@gmail.com", "test123!@");
        authService.joinMember(saveDto);

        // when
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                    .andExpect(status().isNotFound())
                    .andReturn();

        // then
        String content = result.getResponse().getContentAsString();
        String message = objectMapper.readValue(content, ResponseDto.class).getMessage();
        Assertions.assertEquals(message, "wrong email or password");
    }
}